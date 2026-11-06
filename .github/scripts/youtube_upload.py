#!/usr/bin/env python3
"""
Upload + agendamento no YouTube (Lume / canal Mafiizz).

Lê os secrets de variáveis de ambiente:
  YT_CLIENT_ID, YT_CLIENT_SECRET, YT_REFRESH_TOKEN

Uso:
  python .github/scripts/youtube_upload.py --video final.mp4 --version 1.7
  python .github/scripts/youtube_upload.py --video final.mp4 --version 1.7 --dry-run

Dependências:
  pip install google-api-python-client google-auth google-auth-httplib2
"""
import argparse
import os
import random
import sys
import time
from datetime import datetime, timedelta, timezone

# Os imports do Google ficam dentro das funções para o --dry-run funcionar sem as libs.

TOKEN_URI = "https://oauth2.googleapis.com/token"
SCOPES = [
    "https://www.googleapis.com/auth/youtube.upload",
    "https://www.googleapis.com/auth/youtube",
]
REPO_URL = "https://github.com/mafiizz13exe/LauncherClean"
CATEGORY_ID = "28"  # Science & Technology
RETRIABLE_STATUS = {500, 502, 503, 504}


def get_service():
    from google.oauth2.credentials import Credentials
    from googleapiclient.discovery import build

    missing = [k for k in ("YT_CLIENT_ID", "YT_CLIENT_SECRET", "YT_REFRESH_TOKEN")
               if not os.environ.get(k)]
    if missing:
        sys.exit(f"Variáveis de ambiente ausentes: {', '.join(missing)}")

    creds = Credentials(
        token=None,
        refresh_token=os.environ["YT_REFRESH_TOKEN"],
        client_id=os.environ["YT_CLIENT_ID"],
        client_secret=os.environ["YT_CLIENT_SECRET"],
        token_uri=TOKEN_URI,
        scopes=SCOPES,
    )
    return build("youtube", "v3", credentials=creds, cache_discovery=False)


def check_auth():
    """Renova o access token e lê o canal. Falha com mensagem clara se o token expirou."""
    from google.auth.exceptions import RefreshError
    from googleapiclient.errors import HttpError
    service = get_service()
    try:
        resp = service.channels().list(part="snippet", mine=True).execute()
    except RefreshError as e:
        sys.exit("✗ Refresh token inválido/expirado (%s).\n"
                 "  Se o app OAuth está em 'Testing', o token vence em 7 dias: mude para "
                 "'In production' no Google Cloud e gere um novo YT_REFRESH_TOKEN." % e)
    except HttpError as e:
        sys.exit(f"✗ Erro da API do YouTube: {e}")
    items = resp.get("items", [])
    if not items:
        sys.exit("✗ Token válido, mas nenhum canal do YouTube associado a essa conta.")
    print(f"✓ Autenticado no canal: {items[0]['snippet']['title']}")


def build_title(version: str) -> str:
    title = f"Lume: o launcher Android limpo, animado e cheio de efeitos (v{version})"
    return title[:100]  # limite do YouTube


def build_description(version: str, release_url: str | None, extra: str | None = None) -> str:
    lines = [
        f"Lume v{version} — um launcher Android limpo, animado e cheio de efeitos.",
        "",
        "Demonstração gravada automaticamente em um emulador Android.",
    ]
    if release_url:
        lines += ["", f"Baixar o APK: {release_url}"]
    lines += [
        "",
        f"Código-fonte: {REPO_URL}",
        "",
        extra.strip() if extra and extra.strip() else "Música: YouTube Audio Library.",
        "",
        "#Android #Launcher #Lume",
    ]
    return "\n".join(lines)


def build_body(version: str, publish_at: datetime, release_url: str | None,
               tags: list[str], extra: str | None = None) -> dict:
    return {
        "snippet": {
            "title": build_title(version),
            "description": build_description(version, release_url, extra),
            "tags": tags,
            "categoryId": CATEGORY_ID,
            "defaultLanguage": "pt-BR",
            "defaultAudioLanguage": "pt-BR",
        },
        "status": {
            # publishAt só funciona se privacyStatus == "private"
            "privacyStatus": "private",
            "publishAt": publish_at.strftime("%Y-%m-%dT%H:%M:%S.000Z"),
            "selfDeclaredMadeForKids": False,
            "embeddable": True,
            "publicStatsViewable": True,
        },
    }


def upload(service, video_path: str, body: dict) -> dict:
    from googleapiclient.errors import HttpError
    from googleapiclient.http import MediaFileUpload

    media = MediaFileUpload(video_path, chunksize=8 * 1024 * 1024,
                            resumable=True, mimetype="video/mp4")
    request = service.videos().insert(
        part="snippet,status", body=body, media_body=media
    )

    response = None
    retry = 0
    while response is None:
        try:
            status, response = request.next_chunk()
            if status:
                print(f"  upload: {int(status.progress() * 100)}%")
        except HttpError as e:
            if e.resp.status in RETRIABLE_STATUS and retry < 8:
                retry += 1
                wait = random.random() * (2 ** retry)
                print(f"  erro {e.resp.status}, tentando de novo em {wait:.1f}s...")
                time.sleep(wait)
            else:
                raise
        except (ConnectionError, TimeoutError) as e:
            if retry < 8:
                retry += 1
                wait = random.random() * (2 ** retry)
                print(f"  erro de rede ({e}), tentando de novo em {wait:.1f}s...")
                time.sleep(wait)
            else:
                raise
    return response


def main():
    p = argparse.ArgumentParser(description="Upload/agendamento no YouTube")
    p.add_argument("--video", help="caminho do .mp4 final")
    p.add_argument("--version", help="ex: 1.7 (sem o 'v')")
    p.add_argument("--check-auth", action="store_true",
                   help="só valida o token (renova e lê o canal), sem enviar nada")
    p.add_argument("--hours", type=float, default=24.0,
                   help="horas até a publicação (padrão: 24 = +1 dia)")
    p.add_argument("--release-url", default=None,
                   help="URL da Release com o APK (vai na descrição)")
    p.add_argument("--extra-description", default=None,
                   help="texto no lugar da linha de crédito da música (ex: atribuição da faixa)")
    p.add_argument("--tags", default="android,launcher,lume,app android,customização",
                   help="tags separadas por vírgula")
    p.add_argument("--thumbnail", default=None, help="imagem de thumbnail (opcional)")
    p.add_argument("--dry-run", action="store_true",
                   help="só mostra o que seria enviado")
    args = p.parse_args()

    if args.check_auth:
        check_auth()
        return
    if not args.video or not args.version:
        p.error("--video e --version são obrigatórios (exceto com --check-auth)")

    if not os.path.isfile(args.video):
        sys.exit(f"Vídeo não encontrado: {args.video}")

    version = args.version.lstrip("vV")
    publish_at = datetime.now(timezone.utc) + timedelta(hours=args.hours)
    tags = [t.strip() for t in args.tags.split(",") if t.strip()]
    body = build_body(version, publish_at, args.release_url, tags, args.extra_description)

    print(f"Título:      {body['snippet']['title']}")
    print(f"Publicação:  {body['status']['publishAt']} (UTC)")
    print(f"Arquivo:     {args.video} ({os.path.getsize(args.video) / 1e6:.1f} MB)")

    if args.dry_run:
        print("\n[dry-run] nada foi enviado.\n")
        print(body["snippet"]["description"])
        return

    service = get_service()
    print("Enviando...")
    resp = upload(service, args.video, body)
    video_id = resp["id"]
    print(f"Enviado! https://youtu.be/{video_id}")

    if args.thumbnail and os.path.isfile(args.thumbnail):
        from googleapiclient.errors import HttpError
        from googleapiclient.http import MediaFileUpload
        try:
            service.thumbnails().set(
                videoId=video_id, media_body=MediaFileUpload(args.thumbnail)
            ).execute()
            print("Thumbnail definida.")
        except HttpError as e:
            print(f"Aviso: não foi possível definir a thumbnail ({e}).")

    # Saída para o GitHub Actions
    gh_out = os.environ.get("GITHUB_OUTPUT")
    if gh_out:
        with open(gh_out, "a") as f:
            f.write(f"video_id={video_id}\n")
            f.write(f"video_url=https://youtu.be/{video_id}\n")


if __name__ == "__main__":
    main()

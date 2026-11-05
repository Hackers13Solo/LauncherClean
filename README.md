# Clean Launcher

Launcher Android (Kotlin + Jetpack Compose) inspirado no layout enviado:
widget de música, pastas fixas no topo, lista de apps A–Z com barra alfabética, busca e pílulas (bateria, hora, notificações).

## Como gerar o APK

### Opção A — Android Studio
1. Abra a pasta `CleanLauncher` no Android Studio (Koala ou mais novo).
2. Aguarde o Gradle sincronizar → **Build ▸ Build APK(s)**.
3. O APK fica em `app/build/outputs/apk/`.

### Opção B — GitHub (sem instalar nada)
1. Crie um repositório e envie estes arquivos.
2. Aba **Actions ▸ Build APK** → quando terminar, baixe `CleanLauncher-apk` em *Artifacts*.

## Primeiro uso
- Instale o APK, aperte o botão Home e escolha **Clean Launcher ▸ Sempre**.
- Toque no widget de música e ative **Clean Launcher** em *Acesso a notificações*
  (necessário para mostrar a música tocando e o contador de notificações).
- Segure um app na lista → Favoritar / Adicionar à pasta / Info / Desinstalar.
- Toque no **+** ao lado das pastas para criar uma.
- O fundo é o papel de parede do sistema.

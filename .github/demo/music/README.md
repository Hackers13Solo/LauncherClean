# Trilhas do vídeo (YouTube Audio Library)

Coloque aqui arquivos de áudio (`.mp3`, `.m4a`, `.wav`, `.ogg`, `.flac`) baixados da
**YouTube Audio Library** (studio.youtube.com → Biblioteca de áudio → Download).
O pipeline usa uma faixa por dia, em rodízio por ordem alfabética do nome do arquivo,
e repete a faixa em loop se ela for mais curta que o vídeo.

Sem nenhuma faixa aqui, o vídeo **não é enviado** ao YouTube (o pipeline falha de propósito).

## Créditos (só para faixas que exigem atribuição)
Crie `credits.txt` nesta pasta com uma linha por faixa:

    nome-do-arquivo.mp3 | Texto de atribuição exatamente como a Audio Library mostra

Esse texto vai na descrição do vídeo. Sem linha no `credits.txt`, a descrição leva
"Música: <nome da faixa> (YouTube Audio Library)".

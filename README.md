# Lume

Launcher Android (Kotlin + Jetpack Compose): widget de música, pastas, lista de apps A–Z com barra alfabética,
busca, pílulas (bateria, hora, volume) e muitos efeitos visuais.

## Baixar o APK
Aba **Releases** (lado direito da página do repositório) → baixe `Lume.apk` da versão mais recente.
Cada push na branch `main` gera uma nova Release automaticamente (GitHub Actions).
O APK é assinado sempre com a mesma chave, então dá para instalar por cima da versão anterior.

## Novidades da v1.4
**Correção**
- **Favoritos**: ao favoritar um app, o nome não desce mais e o nome/ícone não duplicam. A lista agora usa chaves estáveis por linha e o ícone é recriado quando a linha muda de app.

**20 novidades**
1. Ordem dentro de cada letra: A–Z, mais usados ou mais recentes
2. Seção ★ Favoritos pode ser desligada
3. Favoritos em ordem alfabética ou na ordem em que foram fixados
4. "Mover para o topo dos favoritos" no menu do app
5. Renomear app (apelido)
6. Apelido vazio restaura o nome original
7. Contador "Aberto N vezes" no menu do app
8. Mostrar/ocultar a quantidade em "Todos os apps"
9. Nomes dos apps em destaque (texto mais grosso)
10. Vibrar ao abrir um app
11. Ligar/desligar selos de notificação nos ícones
12. Quantidade de "Mais usados" (3 a 6)
13. Buscador da web à escolha (Google, DuckDuckGo, Bing, Brave)
14. Limpar todos os favoritos
15. "Mostrar todos" nos apps ocultos
16. Copiar backup (favoritos, pastas, ocultos, apelidos)
17. Restaurar backup
18. Compartilhar app
19. Ver app na Play Store
20. Barra A–Z pode ser escondida

## Novidades da v1.3
- **Novo nome: Lume**, com ícone novo (chama em degradê, com versão monocromática para ícones temáticos).
- **Boas-vindas animadas** no primeiro uso: nome, layout, cor, efeitos ao vivo, papel de parede e permissões, com transições e confete. Dá para rever em Ajustes ▸ Sistema e sobre.
- **4 modelos de tela inicial**: Clássico, Grade, Limpo e Compacto (Ajustes ▸ Tela inicial).
- **Papéis de parede em alta qualidade**: busca só traz fotos grandes (ordenadas pela resolução), a pré-visualização carrega a imagem completa e o launcher não usa mais a miniatura como plano B.
- **Categorias com opções dentro**: cada uma das 10 categorias tem 4 artes prontas e fotos da web da categoria para comparar.
- **Câmera no topo**: a lista não passa mais por baixo do furo/notch da câmera.
- **Aurora desligada por padrão** (só as partículas).
- **Barra A–Z**: no fim da lista a letra em destaque agora é a última seção (Z), não mais o W.

## Novidades da v1.2
**Correções**
- **Calculadora** nos atalhos rápidos agora abre em qualquer celular (procura o app certo pelo nome/pacote).
- **Pastas** abrem no **centro** da tela, sem desfocar o fundo (todas as janelas do launcher foram refeitas assim).
- **Aurora** refeita: faixas de luz onduladas e bem visíveis (verde, cor de destaque, azul e roxo).

**Ajustes por categoria** (Aparência, Papel de parede, Efeitos visuais, Tela inicial, Lista de apps, Barras do sistema, Apps ocultos, Sistema e sobre).

**Papel de parede** – no primeiro uso o launcher pergunta qual você quer:
- **Buscar na web**: digite um personagem, filme ou qualquer coisa e ele traz **20 fotos** (filtro de foto + vertical).
- **Opções do sistema**: **10 categorias** prontas (desenho, paisagem, natureza, anime, jogos, terror, Halloween, Natal, Páscoa e espaço).
- Ou escolha da galeria. Dá para trocar depois em Ajustes ▸ Papel de parede.

**5 novidades extras**
1. **Neve e chuva** como novos efeitos de fundo (ligue quantos quiser).
2. **Calculadora na busca**: digite `12*3+4` e veja o resultado (toque para copiar).
3. **Cor de destaque automática** a partir do papel de parede.
4. **Seu nome na saudação** ("Bom dia, Fulano").
5. **Espaçamento da lista** ajustável, e opção de ocultar "Mais usados".

## Novidades da v1.1
1. **Barra A–Z com todas as letras** (★ # A–Z), que se ajusta à altura da tela
2. **Barra A–Z inteligente**: destaca a seção atual, esmaece letras sem apps e amplia as letras perto do dedo (lupa)
3. **Fundo animado**: partículas flutuantes e aurora em movimento (Ajustes ▸ Efeitos visuais)
4. **Cores de destaque**: 8 temas, troca em tempo real
5. **Relógio grande com saudação** (bom dia/tarde/noite), 24h ou 12h
6. **Atalhos rápidos**: lanterna, Wi-Fi, Bluetooth, câmera e calculadora
7. **Tamanho dos ícones e do texto** ajustáveis
8. **Selo NOVO** em apps instalados nos últimos 3 dias
9. **Busca melhorada**: mais usados perto do campo e opção "Pesquisar na web"
10. **Profundidade ao rolar** (linhas das bordas desvanecem e encolhem) + animação ao abrir apps

Também: capa do álbum desfocada e equalizador animado no player, anel de bateria colorido por nível,
vidro com borda em gradiente e balão de letra animado.

## Primeiro uso
- Instale o APK, aperte o botão Home e escolha **Lume ▸ Sempre**.
- Toque no widget de música e ative **Lume** em *Acesso a notificações*.
- Segure um app na lista → Favoritar / Adicionar à pasta / Ocultar / Info / Desinstalar.
- Toque no **+** ao lado das pastas para criar uma.
- Segure a data/relógio ou toque na engrenagem para abrir os **Ajustes**.

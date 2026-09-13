# ROBO MAZE — SURREAL V5

Esta versão reconstrói a partida real do jogo. A imagem aprovada não é usada como um simples fundo: o labirinto, robôs, inimigos, moedas, portal e obstáculos são desenhados e jogados em tempo real.

## O que existe nesta versão
- 30 fases com dificuldade progressiva.
- 6 ambientes visuais: Lab Neon, Ruínas Cyber, Estação Gelo, Núcleo Vulcão, Fábrica Quântica e Reator Void.
- Labirintos maiores, diferentes por fase e com rotas extras nas fases avançadas.
- 6 modelos de robô: Nova, Titan, Pulse, Scout, Orbit e Nexus.
- 8 cores de robô.
- 4 tipos de controle: joystick, deslizar, setas e toque.
- Moedas, portal de saída, inimigos progressivamente mais rápidos e armadilhas energizadas.
- HUD tecnológico, paredes metálicas com profundidade, luzes neon e efeitos de brilho.
- Música de suspense em loop.
- Recorde, desbloqueio de níveis e configurações salvas no aparelho.

## Como gerar o APK no GitHub
1. Extraia este ZIP.
2. Envie TODO o conteúdo para a raiz de um repositório GitHub vazio.
3. A pasta `.github` contém o workflow pronto. Caso ela fique oculta, use também a cópia `WORKFLOW-main.yml`.
4. Abra **Actions** → **ROBO MAZE - GERAR APK** → **Run workflow**.
5. Quando ficar verde, baixe o artefato **RoboMaze-APK**.

O GitHub apenas compila; a lógica e a interface do jogo estão dentro deste projeto.

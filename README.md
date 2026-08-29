# Robo Maze

Jogo Android de labirinto com dificuldade progressiva, 30 níveis, vidas, moedas, inimigos, recordes e seleção de fases.

## Gerar o APK no GitHub
1. Crie um repositório novo no GitHub.
2. Envie **todo o conteúdo deste ZIP para a raiz do repositório**.
3. Abra a aba **Actions**.
4. Entre em **Build Robo Maze APK** e clique em **Run workflow** (ou faça um commit na branch main).
5. Quando o processo ficar verde, abra a execução e baixe o artefato **RoboMaze-APK**.
6. Dentro dele estará `app-debug.apk`, pronto para instalar no Android.

> Este projeto não usa Gradle Wrapper de propósito. O workflow instala a versão correta do Gradle automaticamente, reduzindo problemas com `gradle-wrapper.jar`.

## Música
Esta versão inclui trilha instrumental original de suspense/arcade em loop durante o jogo. O botão SOM no menu liga/desliga a trilha e a preferência fica salva no aparelho.

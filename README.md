# ROBO MAZE — Android

Projeto pronto para GitHub e geração automática do APK.

## Como gerar o APK no GitHub
1. Crie um repositório vazio no GitHub.
2. Envie **todo o conteúdo deste ZIP** para a raiz do repositório, mantendo a pasta `.github`.
3. Faça o commit na branch `main`.
4. Abra a aba **Actions**.
5. Entre em **Build ROBO MAZE APK**.
6. Aguarde o processo ficar verde.
7. Abra a execução concluída e, em **Artifacts**, baixe **ROBO-MAZE-APK**.
8. Dentro dele estará `ROBO-MAZE-v1.0.0.apk`, pronto para instalar no Android.

> O APK gerado é uma build de teste (debug), adequada para instalar diretamente no celular. Para publicar na Play Store, depois será necessária uma build release assinada.

## Incluído
- Android nativo com WebView local/offline
- 100 níveis progressivos
- Labirinto diferente por fase
- Dificuldade crescente
- Moedas, vidas, pontuação e poderes
- Inimigos progressivos
- Controles por botões, teclado e gesto de deslizar
- Salvamento local do progresso
- Capa/arte do ROBO MAZE
- GitHub Actions para gerar APK automaticamente


## Atualização v1.1.0
- Mais rotas de fuga e cruzamentos em todos os labirintos.
- Área do labirinto ampliada na tela do celular.
- HUD, status e controles mais compactos para sobrar mais espaço para o jogo.
- Mantida a progressão de dificuldade por nível.

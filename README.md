# ROBO MAZE

Projeto Android pronto para gerar APK no GitHub Actions.

## Importante
O workflow correto está em `.github/workflows/main.yml` e **não usa `gradlew`**. Ele instala o Gradle 8.7 diretamente no GitHub Actions, evitando o erro `chmod: cannot access 'gradlew': No such file or directory`.

## Como gerar
1. Envie **o conteúdo desta pasta** para a raiz do repositório GitHub (não envie apenas o ZIP como um arquivo).
2. Confirme que existe `.github/workflows/main.yml` no repositório.
3. Vá em **Actions > Build ROBO MAZE APK > Run workflow**.
4. Quando ficar verde, abra a execução e baixe o artefato **RoboMaze-APK**.
5. Dentro dele estará `RoboMaze.apk`.

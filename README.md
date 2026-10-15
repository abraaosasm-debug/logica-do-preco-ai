# Lógica do Preço AI — Android

Aplicativo Android nativo para **diagnóstico de captura de tela e painel flutuante** durante o uso de gráficos em navegador.

## Escopo da versão 0.1.0

- Interface de monitoramento em português (Jetpack Compose).
- Captura de tela após consentimento explícito via MediaProjection.
- Serviço em primeiro plano com notificação e ação para interromper a captura.
- Leitura de quadros e diagnóstico real de frequência, idade do quadro e luminosidade.
- Painel flutuante arrastável e minimizável (permissão de sobreposição).
- Interrupção e limpeza dos recursos.
- Compilação automatizada do APK com GitHub Actions e publicação de pré-release.

**Não possui reconhecimento de candles, inteligência de sinais, CALL ou PUT nesta versão.** A finalidade é provar a compatibilidade de captura e sobreposição antes de desenvolver análise gráfica.

## Teste

1. Instale o APK de debug gerado em Actions ou Releases.
2. Abra o app, conceda a permissão de sobreposição e toque em iniciar captura.
3. Aceite o diálogo de captura de tela do Android.
4. Abra o Chrome e carregue o gráfico que deseja testar.
5. Confira se os contadores de quadros e a taxa de atualização aumentam; teste arrastar e expandir a bolha.
6. Para terminar, use o aplicativo ou **Parar captura** na notificação.

**Atenção:** A autorização de captura pode registrar elementos privados visíveis na tela. Não compartilhe capturas com dados de conta. Nenhuma imagem é enviada nem salva pelo aplicativo. Capturar um app inteiro (quando disponível) pode impedir que a sobreposição apareça nos quadros, o que é desejável para testes.

## Arquitetura

- `MainActivity.kt`: Compose, permissões e início/encerramento.
- `CaptureService.kt`: MediaProjection, VirtualDisplay, ImageReader, notificação e diagnóstico.
- `OverlayService.kt`: janela de sobreposição, arraste, modo compacto/expandido.
- `CaptureStatus.kt`: estado e métricas em StateFlow.

Requer Android 10 ou superior. Teste inicial destinado ao Android 16, sem root, sem automação de corretora. Uso experimental; nenhuma promessa de resultado financeiro.

## Compilação

O workflow `Build Android APK` configura Java 17, Android SDK e Gradle para executar `gradle --no-daemon assembleDebug`.

Sem wrapper Gradle neste repositório, por enquanto. O GitHub Actions instala a versão de Gradle declarada no workflow.

## Próximas etapas

- **v0.2**: calibração e reconhecimento geométrico de candles.
- **v0.3**: regras testáveis conforme o PDF, incluindo comandos e taxas divididas.
- **v0.4**: estados dos gatilhos e alertas fundamentados para testes demonstrativos.

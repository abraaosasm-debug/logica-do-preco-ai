# Lógica do Preço AI — Android

Aplicativo Android nativo para **diagnóstico de captura de tela e painel flutuante** durante o uso de gráficos em navegador.

## Escopo da versão 0.4.0

- Interface de monitoramento em português (Jetpack Compose).
- Captura de tela após consentimento explícito via MediaProjection.
- Serviço em primeiro plano com notificação e ação para interromper a captura.
- Leitura de quadros e diagnóstico real de frequência, idade do quadro e luminosidade.
- Área de análise configurável com controles percentuais.
- Reconhecimento geométrico experimental de candles verdes e vermelhos no recorte.
- Identificação parcial de comandos de preço, nova alta/baixa e observação de retorno à região de 50%.
- Registro de observações na sessão (não persistente).
- Painel flutuante arrastável e minimizável (permissão de sobreposição).
- Interrupção e limpeza dos recursos.
- Compilação automatizada do APK com GitHub Actions e publicação de pré-release.

**O aplicativo possui um algoritmo real e experimental de reconhecimento de candles**, mas NÃO possui validação da Quotex nem leitura de timestamps/valores OHLC de uma API. As etiquetas CALL/PUT são apenas **observações de estudo**, não gatilhos confirmados nem recomendações. O PDF possui mais padrões que ainda precisam ser implementados.

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

- **v0.4**: regras testáveis conforme o PDF, incluindo comandos e taxas divididas.
- **v0.4**: estados dos gatilhos e alertas fundamentados para testes demonstrativos.


## Matrizes de reconhecimento adicionais (v0.4)

Classificador offline independente `ExpandedPatternEngine`:
- Comandos níveis 1–3 e regiões de abertura / metade / fechamento.
- Velas de força por ruptura de extremos visíveis.
- Taxas divididas níveis 1–3 e intermediária (candidatas, contexto incerto).
- Nova posição após reversão, nova alta e nova baixa.
- Retorno visual às regiões de defesa.
- Classificação geométrica preliminar de topos e fundos / tendência.

**Nenhum desses reconhecimentos significa probabilidade de ganho ou confirmação de entrada.** O aplicativo não recebe timestamps de abertura da Quotex, OHLC oficial, nem histórico completo. Não é correto usar um sinal em tempo real para dinheiro real até que a calibração e os resultados sejam validados em condições reais e teste fora da amostra.

A fidelidade às 223 páginas do PDF é um objetivo do projeto, não uma funcionalidade totalmente concluída nesta versão. Não há execução de ordens, leitura automatizada de credenciais, API não oficial nem promessas de acerto. O usuário deve testar na conta demonstrativa.

## Como validar com dados reais

1. Configure o gráfico M1 em cores verde/vermelho e candles legíveis.
2. Selecione a região do gráfico no aplicativo, sem controles do navegador.
3. Compare manualmente os candles detectados com os visíveis.
4. Registre falsos positivos e falsos negativos por tipo de padrão.
5. Confira atrasos, perdas de quadros e mudanças de orientação.
6. Use somente conta demonstrativa durante a validação.



## Correções v0.4 baseadas em testes no Android

As capturas do usuário mostraram dezenas de candles no gráfico enquanto a v0.3 detectava apenas um. A causa identificada era o agrupamento horizontal de pixels coloridos, que mesclava velas próximas. A v0.4 separa corpos por predominância cromática e continuidade vertical, com testes sintéticos específicos para gráficos densos.

- Opção de perfil Quotex baseado nos prints enviados: recorte 2%–73% horizontal, 30%–73% vertical, editável. É apenas um ponto de partida.
- Painel expandido reduzido a 244dp para obstruir menos o gráfico.
- Títulos e informações corrigidos para contraste no tema escuro.
- Informações de nitidez geométrica **não são acurácia de sinais**. Com menos de oito candles, a indicação é "Dados insuficientes".
- Instrução para escolher "Tela inteira" ao autorizar captura em Android, evitando suspensão quando o usuário alterna o app e o Chrome.
- Interface atualizada para v0.4.

**A captura foi comprovada nas imagens enviadas pelo usuário, mas o algoritmo novo AINDA precisa de teste em aparelho.** Compilação e testes sintéticos não validam desempenho na Quotex. Não operar dinheiro real com sinais experimentais.

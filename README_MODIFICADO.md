# AI Vocal Remover — integração de separação local

O fluxo principal agora utiliza **separação local de áudio** com `web-audio-separation` (MDX-Net + ONNX Runtime Web), em vez de enviar a música para `aivocalremover.com`.

## Como funciona

1. O usuário escolhe MP3, WAV, FLAC, M4A, AAC, OGG ou OPUS no seletor Android.
2. O FFmpegKit decodifica o áudio para WAV PCM float estéreo a 44,1 kHz no cache privado do aplicativo.
3. Uma tela WebView executa o modelo `UVR-MDX-NET-Voc_FT` com ONNX Runtime Web (WebGPU quando disponível, fallback WASM/CPU).
4. As faixas de vocal e instrumental podem ser salvas como WAV ou FLAC na pasta `Música/AI Vocal Remover` em Android 10+. O FLAC é codificado sem perdas a partir do WAV gerado pela IA.

**A música não é enviada para um servidor de separação.** O runtime JavaScript e o modelo são baixados de hospedagens externas. A primeira inicialização precisa de internet; a disponibilidade de cache depende do WebView. Portanto, esta versão já remove o endpoint remoto de processamento do caminho principal, mas ainda não é uma versão completamente independente da rede para carregar o motor/modelo.

## Requisitos e limitações

- Android 7.0 (API 24) ou superior.
- Uma conexão à internet para buscar a biblioteca e o modelo se ainda não estiverem em cache.
- Memória livre suficiente: inferência de separação é pesada. Arquivos longos podem consumir bastante RAM; não feche o app durante a execução.
- O modelo processa áudio em 44,1 kHz estéreo. A decodificação FLAC é feita sem compressão com perdas, mas o reamostramento para 44,1 kHz e a separação por IA alteram o conteúdo de áudio; os resultados WAV não são uma cópia bit a bit do original.
- Os resultados podem ser exportados em WAV ou FLAC. O FLAC preserva exatamente as amostras do WAV produzido pela IA, mas a separação, a reamostragem para 44,1 kHz e o formato de saída não preservam o áudio original bit a bit.
- A `MainActivity` antiga foi convertida em um redirecionamento de compatibilidade; as chamadas Retrofit para o servidor de processamento e as classes de upload remoto foram removidas do projeto.

## Compilar

Abra o projeto no Android Studio com JDK 17 e Android SDK compatível com `compileSdk 35`, sincronize Gradle e execute `assembleDebug`. A compilação do APK não foi validada neste ambiente porque o SDK/artefatos de build necessários não estavam disponíveis localmente e a rede do ambiente não permitiu baixá-los.

## Créditos/licenças

- `web-audio-separation`: projeto MIT, https://github.com/incidentist/web-audio-separation
- Ultimate Vocal Remover GUI: https://github.com/Anjok07/ultimatevocalremovergui — código MIT; o projeto UVR pede atribuição a seus desenvolvedores para aplicações de terceiros.
- O arquivo de pesos `UVR-MDX-NET-Voc_FT` é obtido do registro de modelos usado pela biblioteca. Confira a licença específica dos pesos e preserve as atribuições necessárias antes de distribuir uma versão publicada.

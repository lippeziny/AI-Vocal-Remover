# Alterações — AI Vocal Remover

## Integração local de separação

- Adicionada `LocalSeparatorActivity.kt`, uma tela WebView dedicada à separação no dispositivo.
- Adicionado `app/src/main/assets/local_separator.html`, que carrega `web-audio-separation` (MDX-Net + ONNX Runtime Web) e executa o modelo UVR `UVR-MDX-NET-Voc_FT` usando WebGPU quando disponível e WASM/CPU como fallback.
- O botão principal de extração não chama mais o endpoint `aivocalremover.com`: ele normaliza o áudio localmente com FFmpegKit e abre o separador local.
- A saída é apresentada com prévia de áudio e é possível salvar as faixas WAV ou FLAC na pasta Música/AI Vocal Remover. A conversão WAV → FLAC é feita pelo FFmpegKit sem perdas em relação às amostras produzidas pelo modelo.
- A implementação limita o caminho de entrada da WebView a um arquivo em cache privado; o áudio não é enviado para a URL que hospeda o runtime ou o modelo.

## Formatos

A tela aceita MP3, WAV, FLAC, M4A, AAC, OGG e OPUS. FFmpegKit decodifica a entrada para WAV PCM float estéreo a 44,1 kHz para alimentar o modelo. Essa etapa evita nova quantização inteira antes da inferência, mas reamostragem, conversão de canais e a própria IA modificam o áudio. O FLAC de saída é lossless em relação ao WAV calculado pela IA, não em relação ao arquivo original bit a bit.

## Rede e limitações

A execução de inferência é local. Na inicialização, o código da biblioteca é importado por HTTPS e os pesos do modelo são baixados da hospedagem configurada pela biblioteca. A primeira execução requer conexão; o cache do WebView pode reduzir downloads posteriores, mas esta versão não inclui runtime e pesos dentro do APK e não promete funcionamento totalmente offline. O processamento de arquivos grandes depende da memória e velocidade do aparelho.

## Verificação

- XML de `AndroidManifest.xml` e layout alterados validados sintaticamente.
- O APK não foi compilado ou testado em um dispositivo neste ambiente (SDK/artefatos não estavam disponíveis localmente e o ambiente não podia resolver o host de download do Gradle).

- Removidos `MyAPI.kt`, `UploadRequestBody.kt`, os modelos de resposta remota e as dependências Retrofit/Gson; a antiga `MainActivity` só redireciona para a tela principal.
- Removida a configuração antiga que permitia tráfego HTTP sem criptografia e aceitava certificados de usuário; os recursos externos usados pela integração utilizam HTTPS com a validação padrão de certificados do Android.

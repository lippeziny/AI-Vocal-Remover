# AI Vocal Remover — separação local

Fork Android do AI Vocal Remover com remoção de anúncios e do fluxo de créditos local, agora com um caminho principal de separação de áudio executado no aparelho.

## Recursos

- Separação de voz e instrumental com um modelo MDX-Net compatível com UVR, executado via ONNX Runtime Web na WebView.
- Entrada MP3, WAV, FLAC, M4A, AAC, OGG e OPUS; FFmpegKit normaliza a entrada em WAV para o modelo.
- Prévia e exportação das faixas resultantes em WAV ou FLAC (o FLAC é codificado sem perdas a partir da saída produzida pelo modelo).
- O áudio da música não é enviado a um servidor de processamento.

## Importante sobre conexão e desempenho

O runtime JavaScript é carregado por HTTPS e os pesos do modelo (~67 MB) são obtidos da hospedagem usada pela biblioteca. A primeira execução precisa de internet; o peso pode ser armazenado no Cache Storage, mas o runtime remoto também pode precisar ser recarregado se o cache for removido. Assim, a inferência é local e não depende de um serviço que processe a música, porém esta versão não garante operação totalmente offline desde uma instalação limpa.

A separação por IA altera o áudio; exportar o resultado em FLAC preserva sem perdas as amostras do WAV gerado pelo modelo, mas não recupera nem preserva bit a bit o FLAC original. Arquivos longos requerem memória e processamento consideráveis.

## Referências e licenças

- UVR GUI (código MIT): https://github.com/Anjok07/ultimatevocalremovergui
- `web-audio-separation` (MIT; port de separação MDX-Net para navegador): https://github.com/incidentist/web-audio-separation
- ONNX Runtime Web: https://onnxruntime.ai/

Consulte `MODIFICACOES_PT-BR.md` para detalhes, limitações conhecidas e instruções para build.

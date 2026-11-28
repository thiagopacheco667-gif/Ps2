# Nexus2 — notas do projeto

Fork Android do [ARMSX2](https://github.com/ARMSX2/ARMSX2) (GPL-3.0), que por sua vez é
baseado no [PCSX2](https://pcsx2.net). Importado sem modificações do commit `46f1575`
(2026-11-25).

## Decisões técnicas

| # | Decisão | Motivo |
|---|---------|--------|
| 1 | O app Android do ARMSX2 (`platforms/android`) é a base; não reescrever a interface. | Ele já tem biblioteca, BIOS, saves, controles, OSD etc. Reescrever apagaria código funcional. |
| 2 | Núcleo (`pcsx2/`, `common/`, `3rdparty/`) fica intocado. | Requisito do projeto; preserva correção e JIT ARM64. |
| 3 | Nome exibido: **Nexus2** (provisório). | "PS2"/"PlayStation" são marcas da Sony. |
| 4 | Recursos de rede ficarão **desligados por padrão**. | Seção 9 da especificação (privacidade). |
| 5 | Mali-G720 usa o driver Vulkan de fábrica; MaliSX2 não suporta a 5ª geração de Mali. | Ver `CustomDriver.kt`. Turnip não se aplica a Mali. |
| 6 | Build pelo GitHub Actions (`.github/workflows/android-apk.yml`). | O ambiente de desenvolvimento não alcança os servidores do Android SDK/Gradle. |
| 8 | `applicationId` = `com.nexus2`; o namespace Kotlin `com.armsx2` fica. | Instala ao lado do ARMSX2 sem renomear centenas de arquivos. O manifest já usa `${applicationId}`. |
| 9 | Intro em vídeo do ARMSX2 desligada por padrão (pode ser ligada nas configurações). | É marca do ARMSX2. |
| 10 | Textos sobre o driver MaliSX2/Turnip e o envio de bug reports continuam dizendo "ARMSX2". | São serviços do ARMSX2; dizer "Nexus2" seria falso. |
| 11 | Remoção de motion blur via patches que já vêm no `patches.zip` (≈190 jogos), ligados por jogo depois do boot (`BlurRemoval.kt`). | Antes do boot, `setEnabledPatches` cairia na camada base e ligaria o patch por nome em todos os jogos. Patches de "uma vez no carregamento" valem a partir do 2º boot. |
| 12 | Perfis rápidos só preenchem opções que já existem (`QuickProfiles.kt`). | Nada novo no núcleo; tudo continua ajustável à mão. |
| 13 | Barra inferior (retrato) / trilho lateral (paisagem) sobreposta ao conteúdo, só nas 4 seções (`MainNavBar.kt`). | O `AnimatedContent` não muda de lugar na árvore, então as telas não perdem estado quando a barra some numa sub-tela. |
| 14 | Sub-telas abertas pelo hub Ferramentas voltam ao hub (`UiNavigator.openFrom/up`); vindas do menu lateral ou do menu do jogo voltam ao Início, como antes. | Menu lateral continua como alternativa. |
| 15 | L1/R1 trocam de seção com a biblioteca na frente, exceto na grade de Jogos (lá seguem trocando ordenação/modo, como no ARMSX2) e nos Ajustes (abas). SELECT avança de seção em qualquer tela principal. | Não rouba L1/R1 do jogo nem da grade. |
| 16 | Favoritos guardados por URI do jogo (`Favorites.kt`), como os ocultos; duplicados = mesmo serial (`Duplicates`), jogos sem serial nunca contam. | Mesma identidade que o app já usa; sobrevive a novas varreduras. |
| 17 | Painel do Início só com dados reais: recentes/tempo de jogo (`PlayTime`), BIOS selecionada, RAM/armazenamento/temperatura da bateria lidos do Android a cada 5 s. | Nada de valores de exemplo. |
| 18 | Tocar numa capa (ou A no controle) abre a página do jogo; JOGAR já vem focado, então são dois toques/dois A. O menu de segurar a capa continua igual. | Pedido da especificação; nada removido. |
| 19 | Ajustes: as 11 abas originais agrupadas em 7 seções (Aparência, Desempenho, Gráficos, Áudio, Controles, Sistema, Avançado), sem mover nenhuma opção. Modo Simples (padrão) mostra só perfis e nitidez; a busca muda para Avançado. | Preserva todas as opções e o escopo Global/Por jogo. |
| 20 | Volta com pilha (`UiNavigator.parents`): Jogos → jogo → Ajustes do jogo volta passo a passo. | |
| 21 | Assinatura fixa: `platforms/android/app/nexus2-debug.keystore` (senha pública "android", como a chave de debug do Android) e `versionCode = 2000 + run_number`. | A chave de debug padrão era gerada a cada build no CI, e o Android recusava atualizar ("conflito com o pacote"). Não é segredo nem serve para loja. |
| 7 | Agendamento diário do `nightly.yml` do upstream desativado. | Ele publica nightlies do ARMSX2 e gastaria minutos do Actions. |

## Etapas

1. [x] Compilar o APK original sem alterações.
2. [x] Rebrand (PR #2): nome, applicationId, ícone, paleta, splash, Sobre/licenças.
3. [~] Polimento: logo no app, traduções com o nome Nexus2, perfis rápidos, remoção de blur (PR #3).
4. [~] Perfil "Equilíbrio" (2x, blending Basic) como recomendado para Dimensity 8400 / Mali-G720.
5. [~] Rede: atualizador e notícias apontam para este repositório; checagem automática de driver é opt-in. Discord, RetroAchievements e "checar atualização ao abrir" já eram opt-in. Capas continuam baixando do GitHub (xlenore).
6. [ ] Testes no Poco X7 Pro.

## Redesign

- Canvas do sistema de design: https://claude.ai/artifact/GN1gUSNnTeGU74ES7vGkPP
- [x] Etapa 3: sistema de design e mockups aprovados.
- [x] Etapa 4: navegação (PR #4).
- [x] Etapa 5: painel do Início, favoritos e duplicados (PR #5).
- [~] Etapas 6–10 (PR #6): página do jogo, ferramentas em grupos + Diagnóstico, Ajustes em 7 seções com modo Simples/Avançado, navegação com pilha de volta.

## Redesign 2 (cinematográfico)

- [x] Etapa 1: componentes compartilhados `ui/nexus/NexusUi.kt` (linha, divisória, seção, chip, botões, fundo do jogo, compatibilidade). Perfis unificados: Compatibilidade, Equilibrado, Desempenho + Personalizado.
- [x] Etapa 2: Home centrada no último jogo (capa ampliada e desfocada como arte, pois só há box art), recentes, estado do sistema em linhas, atividade recente (último save, último jogo, última atividade).
- [~] Etapa 3: biblioteca com filtros Todos/PS2/Favoritos/Recentes e contagem; página do jogo com arte, região, formato, tamanho do arquivo, JOGAR, Configurações/Save States/Memory Card e bloco de informações.
- [~] Etapa 4: barra Início/Jogos/Saves/Ajustes; aba Saves separando Save States (lista real das pastas) e Memory Cards.
- [~] Etapa 5: Ajustes em linhas (Gráficos, Emulação, Áudio, Controles, Sistema, Desempenho e diagnóstico, Perfil atual); cada uma abre sua página com as abas reais.
- [~] Módulo por jogo: cabeçalho (capa, região, perfil, restaurar padrão), só Gráficos/Emulação/Áudio/Controles, "↺ Global" por opção (remove só aquela chave do arquivo do jogo e regrava o INI), perfis por jogo.
- [~] Etapa 6: menu de pausa mantido (já tem salvar/carregar estado, vídeo, avanço rápido, indicadores); só a cor fixa trocada pelo tema. Controles virtuais sem mudança de lógica.
- [~] Desempenho e diagnóstico: FPS, VPS, velocidade, tempo de frame, uso de GPU, backend, resolução e memória ao vivo (só com jogo aberto), temperaturas.

## Refinamento 3 (biblioteca e configurações durante a emulação)

- [~] Fase 1: menu do jogo em Geral (só ações), Vídeo (renderizador/resolução/proporção/filtros primeiro, resto recolhido), Emulação (compatibilidade + correções avançadas recolhidas), Desempenho, Controles (+ OSD), Sistema (saves, memory cards, patches, info do jogo, GS dump, relatório). Abas com ícone + texto, descrições em 1 linha, seções planas, cabeçalho com "Configurações personalizadas" quando o jogo tem ajustes próprios.
- [~] Fase 2: Biblioteca sem a seção de recentes (o histórico continua e aparece só no Início); filtros Todos / Favoritos / regiões presentes; toque abre a página do jogo; segurar: Favoritar, Configurações individuais, Informações, Remover da biblioteca (é o "ocultar": o arquivo nunca é apagado; ⋮ → Mostrar jogos removidos).
- [~] Fase 3: Inter (OFL) para textos/valores, fonte de título mantida; o switch global de patches de blur não aparece mais nas páginas por jogo; GPUs sem fonte de driver (Mali-G720 e outras Mali 5ª geração) não veem lista de downloads, opções Turnip nem texto de Adreno, e o driver do sistema nunca é trocado sozinho; textos de Ajustes → App dizem se afetam Início ou Biblioteca.

## Fundo com a arte do jogo

- O fundo do Início e da Biblioteca segue o jogo em execução e, se nenhum estiver rodando, o último jogado: a capa ampliada, com desfoque (Android 12+) e gradiente escuro. Sem histórico, ou se a capa não carregar, fica um fundo estático do Nexus2 (preto com brilho violeta/azul). Nunca a arte de outro jogo.
- Não há arte de cena no repositório de capas, então a "arte" é a capa (a capa personalizada do usuário primeiro).
- A imagem é decodificada pequena (270×400) e fica no cache de memória e disco do Coil. A única animação é um crossfade de 450 ms quando o jogo muda.
- Removidos do caminho padrão: onda XMB (GL), onda 2D, partículas e protetores como fundo. Os arquivos continuam no código (o protetor de tela por inatividade segue existindo). Imagem de fundo própria do usuário continua tendo prioridade.

## NEXUS PERFORMANCE — Fase 1 (auditoria) e medição

- Auditoria (2 agentes, só leitura): CPU/ARM64 e Mali-G720. Principais achados: regra MediaTek desliga framebuffer fetch no Vulkan (palpite, só o 8300 isento); "Auto" escolhe OpenGL na G720; Mali sem dual-source faz blend em software mesmo no Mínimo; 3 opções de VU na UI não são lidas pelo núcleo; "Desempenho sustentado" troca a afinidade 7→0; nice −1 não aplicado no modo 7.
- Medição: pause → Desempenho → Analisar desempenho. Comparar = estado privado + mesma cena, 3 configurações (6 s aquecimento + 20 s), contadores do núcleo a 4 Hz; Sustentado = 1º e último minuto de 10 min. Configurações de teste só como overrides do jogo; blob original restaurado byte a byte (e recuperado na próxima abertura se o app morrer). Nada estimado: sem dado = "Não disponível" (1% lows não existem no núcleo).
- Página Ajustes → Nexus Performance: estado do sistema, ao vivo, "O que está limitando seu jogo?" (só com evidência), resultados, diagnóstico do log do núcleo.

### Medições reais (Poco X7 Pro, MT6899, Mali-G720 MC7, driver r49p1)

- Log: fbfetch=NO(barrier-fallback) confirmado (regra MediaTek); dualSrc=yes (a auditoria supunha não); afinidade 0xf0 com núcleo prime 0x80 separado.
- Yakuza (SLUS-21348), mesma cena: 2x Médio 99,2% (10% do tempo <97%, mín 89%); 2x Básico 100%/0%; 1x Mínimo 100%, GPU 24%. Jogo fixo em 30 FPS.
- Tony Hawk's Downhill Jam (SLUS-21456): MTVU off 53,7 FPS do jogo; MTVU on 37,5 (2x Básico) e 36,3 (1x Mínimo). EE 91% com MTVU off, 55% com on.
- Decisões: MTVU saiu dos perfis (decidido por jogo); "Otimizar este jogo" testa uma variável por vez (MTVU, blending→Básico, resolução ±1) na mesma cena e aplica só o vencedor no jogo, com Desfazer; padrão global Mali = blending Básico (botão, reversível). Rótulos corrigidos: getVPS() = FPS interno do jogo.

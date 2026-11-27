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
| 15 | L1/R1 trocam de seção só com a biblioteca na frente; nos Ajustes continuam trocando de aba. | Não rouba L1/R1 do jogo. |
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
- [~] Etapa 4: navegação (PR #4). Até a etapa 5, Início e Jogos mostram a mesma biblioteca.

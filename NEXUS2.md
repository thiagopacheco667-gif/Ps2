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
| 7 | Agendamento diário do `nightly.yml` do upstream desativado. | Ele publica nightlies do ARMSX2 e gastaria minutos do Actions. |

## Etapas

1. [ ] Compilar o APK original sem alterações (em andamento).
2. [ ] Rebrand: nome, applicationId, ícone, paleta, splash, Sobre/licenças.
3. [ ] Polimento da interface.
4. [ ] Perfil Dimensity 8400 / Mali-G720.
5. [ ] Recursos de rede desligados por padrão.
6. [ ] Testes no Poco X7 Pro.

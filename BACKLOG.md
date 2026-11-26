# Backlog do Projeto — One Inventory, Many Regrets

> Documento de organização de tarefas (mecânicas novas, correções críticas, refatoração arquitetural e pendências de publicação).  
> **Nota (atualizada em 26/11/2026):** Os 4 épicos (`BL-001` a `BL-013`) tiveram sua implementação principal concluída, mas a auditoria pós-implementação identificou pendências abertas, casos de borda, lacunas de testes/SOLID e trechos desatualizados na documentação (detalhados na seção final deste arquivo). Consulte também [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md).

## Legenda

- **Prioridade:**
  - **P0 (Crítica):** bloqueia publicação responsável ou envolve risco de duplicação/perda de itens e conformidade legal.
  - **P1 (Alta):** impacto direto em jogabilidade justa, consistência multiplayer ou confiança na release.
  - **P2 (Média):** melhoria importante de design, manutenibilidade, desempenho ou confiabilidade operacional.
  - **P3 (Baixa):** melhoria desejável sem bloqueio imediato.
- **Esforço estimado:** `P` (horas), `M` (1–3 dias), `G` (vários dias / mudança estrutural).

---

## Visão Geral

| ID | Título | Épico | Prioridade | Esforço | Status (26/11/2026) | Ref. Auditoria |
| --- | --- | --- | :---: | :---: | --- | --- |
| `BL-001` | Desligamento seguro de sync em runtime sem duplicar itens/efeitos | Integridade & Anti-Duplicação | P0 | G | Concluído (`README.md`/`TECHNICAL_DEBT.md` alinhados; sobra decisão menor de `syncEffects`) | `TD-001` |
| `BL-002` | Reserva/consumo determinístico de munição e itens secundários ao iniciar uso | Integridade & Anti-Duplicação | P1 | M/G | Concluído (disconnect, morte e troca de equipe cobertos em GameTest) | — |
| `BL-003` | Cooldown individual em arremessáveis + consumo imediato do último item | Integridade & Anti-Duplicação | P1 | M | Concluído (guardas em `ConsumableMixin` e `useItemOn` + GameTests adicionados) | — |
| `BL-004` | Efeitos replicados individuais para afinidade parcial (`0% < N < 100%`) | Mecânicas & Balanceamento | P2 | G | Implementado (restam apenas traduções experimentais via `BL-011`) | — |
| `BL-005` | Setting de dano de lava para múltiplos membros (`1x` cap vs. `Nx`) | Mecânicas & Balanceamento | P2 | P | Concluído | — |
| `BL-006` | Setting de fôlego subaquático para múltiplos membros (`1x` cap vs. `Nx`) | Mecânicas & Balanceamento | P2 | P | Concluído (coberto com Respiração e `DROWN` em `50%` de afinidade) | — |
| `BL-007` | Setting de DoTs para múltiplos membros (`1x` cap vs. `Nx`) | Mecânicas & Balanceamento | P2 | P/M | Concluído (`DRAGON_BREATH` incluído + GameTests em `50%` de afinidade) | — |
| `BL-008` | Plano de refatoração do núcleo monolítico seguindo princípios SOLID | Arquitetura & Confiabilidade | P2 | G | Parcial (ISP aplicado em `HiveMindContexts`; restam `HiveMind`/`OIMRConfig`) | `TD-011` |
| `BL-009` | Política e expurgo de hives e estados pessoais órfãos | Arquitetura & Confiabilidade | P2 | M/G | Concluído (`TD-007` atualizado; opcional P3: expurgo automático/backup) | `TD-007` |
| `BL-010` | Escrita atômica de config e correção do fallback no reload | Arquitetura & Confiabilidade | P2 | P/M | Concluído (`TD-009` atualizado; opcional P3: auto-restore de `.bak`) | `TD-009` |
| `BL-011` | Política de traduções (suporte oficial + aviso IA + chaves faltantes) | Qualidade, Docs & Licença | P0 | M | Implementado (`README.md` atualizado; 143 rascunhos aguardam revisão) | `TD-002` |
| `BL-012` | Escopo explícito de licença para terceiros e inventário de procedência | Qualidade, Docs & Licença | P0 | M | **Pendente P0** (`ASSET_PROVENANCE.md` aguarda confirmação do titular) | `TD-003` |
| `BL-013` | Testes automatizados e roteiro manual de Cliente, UI e Áudio | Qualidade, Docs & Licença | P1 | M/G | **Parcial P1** (22 JUnit + UI coupling verdes; falta matriz manual e HUD/TAB) | `TD-004` |

---

## Épico 1 — Integridade de Itens, Consumo Autoritativo e Anti-Duplicação

### `BL-001` — Alteração de opções de sync em runtime sem duplicação de itens (e demais estados)
- **Prioridade:** P0 | **Esforço:** G | **Relação:** `TD-001` (`OIMRConfig.java`, `OIMRSettingsScreen.java`, `HiveMindManager.java`)
- **Problema:** Alterar opções de sincronização de slots (`syncHotbar`, `syncMainHand`, `syncOffHand`, `syncHelmet`, `syncChestplate`, `syncLeggings`, `syncBoots`) em runtime — além de entrada, saída e troca de equipe — pode gerar duplicação ativa de itens entre membros do grupo. O mesmo risco conceitual precisa ser auditado para efeitos (`syncEffects`) e outros recursos compartilhados ao alternar settings em tempo real.
- **Comportamento desejado:**
  - Antes de desligar a sincronização de qualquer slot em runtime, **dropar o item atualmente sincronizado no chão para o jogador que alterou a configuração** (ou para o jogador com prioridade definida no grupo quando a mudança vier de console/RCON/host sem inventário ativo).
  - Somente **após** o drop/remoção atômica do slot compartilhado, aplicar a mudança da setting e liberar o slot como individual, evitando janela de condição de corrida entre os `N` membros.
  - Auditar outros aspectos compartilhados que possam ser duplicados ao ligar/desligar settings em runtime (ex.: efeitos ativos em `syncEffects`, armadura equipada, itens na mão durante uso contínuo).
- **Subtarefas:**
  - [x] Definir regra determinística de prioridade (quem recebe o drop quando a mudança vem de `/oimr settings`, tela de host ou console/RCON).
  - [x] Implementar transição transacional de slot compartilhado → drop canônico no mundo → desativação da flag de sync, bloqueando reentrada/corrida no tick.
  - [x] Mapear e proteger outros estados com risco de duplicação na troca de settings (efeitos ativos, container aberto, item sendo usado).
  - [x] Atualizar textos da UI/config (`oimr.settings.slots_duplication_warning*` e comentários de `OIMRConfig`) após corrigir o comportamento.
  - [x] Criar GameTests de conservação de itens ao alternar cada `sync*` em runtime com 2+ jogadores.

---

### `BL-002` — Consumo/reserva determinística de munição e itens secundários ao iniciar uso (ex.: arco e flecha)
- **Prioridade:** P1 | **Esforço:** M/G
- **Problema:** Ferramentas que dependem de um item secundário (ex.: arco consumindo flecha, besta, etc.) permitem que dois membros de um grupo com apenas 1 unidade do item secundário iniciem o uso simultaneamente e gerem inconsistência ou duplicação de disparo.
- **Comportamento desejado:**
  - No momento exato em que um jogador começa a utilizar a ferramenta (ex.: iniciar o *draw* do arco), o item secundário deve contar imediatamente como reservado/consumido para os demais jogadores do grupo, de forma **determinística e autoritativa no servidor**.
  - Se um grupo de 2 tem 1 arco (ou 2 arcos) e 1 flecha e o Jogador A inicia o *draw*, caso o Jogador B tente puxar o arco, a flecha não deve mais estar disponível no inventário compartilhado.
  - **Consistência visual/funcional do usuário ativo:** o jogador que iniciou o *draw* não deve perder a flecha de forma inesperada no próprio cliente durante a puxada (ex.: avaliar reserva lógica no servidor associada ao jogador ativo, ou movimentação controlada para estado/slot reservado).
  - **Cancelamento:** ao cancelar a ação sem disparar, tentar devolver a 1 unidade reservada ao inventário compartilhado; se não houver espaço disponível no momento do cancelamento, **dropar o item no pé do jogador de forma canônica** (mesmo fluxo de `player.drop(...)`, não apenas spawn cru da entidade).
- **Subtarefas:**
  - [x] Levantar todas as ferramentas/ações que consomem item secundário no uso (arco/flechas normais/espectrais/com efeito, besta/fogos, etc.).
  - [x] Projetar mecanismo autoritativo no servidor de reserva por jogador em uso ativo, garantindo sincronização imediata com os demais membros.
  - [x] Garantir fluxo de devolução ao inventário ou drop canônico no cancelamento, desconexão ou troca de slot/morte durante o *draw*.
  - [x] Cobrir com GameTests (2 jogadores disputando a última flecha, cancelamento com inventário cheio, disparo normal).

---

### `BL-003` — Cooldown individual para arremessáveis/consumíveis com consumo imediato do último item
- **Prioridade:** P1 | **Esforço:** M
- **Problema:** Precisa ficar claro e garantido que o cooldown de itens arremessáveis (bola de neve, ender pearl, etc.) não trave os colegas, mas sem abrir brecha para dois jogadores arremessarem o mesmo último item no mesmo tick/janela.
- **Comportamento desejado:**
  - **Cooldown não propagado:** o cooldown de arremesso/uso (ex.: ender pearl, bola de neve, poções arremessáveis, olho do ender, etc.) permanece individual por jogador.
  - **Consumo imediato em grupo:** seguindo a mesma linha determinística de `BL-002`, assim que o último item é lançado/consumido por um membro, ele já deve constar imediatamente como consumido para o restante do grupo, impedindo que a mesma ender pearl (ou outro consumível) seja usada/arremessada 2x.
- **Subtarefas:**
  - [x] Verificar como o fluxo atual propaga (ou bloqueia) `ItemCooldowns` entre membros e assegurar isolamento por jogador.
  - [x] Auditar todos os itens arremessáveis e consumíveis de uso instantâneo/contínuo para garantir baixa imediata no inventário compartilhado e propagação síncrona para o grupo antes de permitir o uso de outro membro no mesmo tick.
  - [x] Criar GameTests testando dois jogadores tentando arremessar simultaneamente a última ender pearl e a última bola de neve, além de verificar independência do cooldown quando há 2+ unidades.

---

## Épico 2 — Mecânicas de Afinidade, Dano e Sobrevivência

### `BL-004` — Efeitos replicados individuais para afinidade parcial (`0% < N < 100%`)
- **Prioridade:** P2 | **Esforço:** G
- **Contexto:** Ajustar genericamente a proporção de efeitos vanilla (especialmente efeitos com comportamento mecânico/binário) gera resultados estranhos ou difíceis de calibrar. A proposta é criar **efeitos replicados próprios** do mod para representar a transmissão parcial (`0% < afinidade < 100%`), tratados individualmente por efeito cadastrado no jogo.
- **Exemplo de referência:** Efeito original **Invisibilidade** → efeito replicado **Translucidez**, que reduz o raio de detecção de mobs conforme a afinidade em vez de aplicar invisibilidade total ou falhar binariamente.
- **Subtarefas:**
  - [x] Inventariar todos os efeitos (`MobEffect`) vanilla registrados no jogo e classificar:
    - Efeitos puramente numéricos/escaláveis (dano, velocidade, força, resistência, etc.).
    - Efeitos mecânicos/binários que exigem contraparte dedicada (ex.: Invisibilidade → Translucidez, Visão Noturna, Respiração Aquática, Queda Lenta, Brilho, etc.).
    - Política de fallback para efeitos de outros mods ou não mapeados.
  - [x] Definir curva/regra de afinidade (`0% < N < 100%`) para duração, amplificador e intensidade da contraparte.
  - [x] Registrar os novos `MobEffect`s customizados (identificadores, ícones/texturas, cores, nomes e descrições traduzíveis).
  - [x] Garantir que ao mudar afinidade para `100%` ou `0%`, ou ao sair do grupo, os efeitos replicados sejam limpos/substituídos sem vazamento de estado.
  - [x] Documentar no `README.md` e cobrir com GameTests os principais efeitos mecânicos.

---

### `BL-005` — Setting de dano de lava para múltiplos membros (`1x` cap vs. `Nx` multiplicativo)
- **Prioridade:** P2 | **Esforço:** P
- **Comportamento desejado:** Nova opção de configuração com duas modalidades quando `N > 1` membros caem na lava simultaneamente:
  - **Cap em `1x` (padrão inicial):** o grupo sofre no máximo o equivalente a `1 × dano` de lava compartilhado, evitando morte instantânea injusta quando todos caem juntos.
  - **Multiplicativo (até `N × dano`):** cada membro na lava contribui com sua instância de dano propagada ao grupo (comportamento punidor/caótico).
- **Subtarefas:**
  - [x] Definir chave em `OIMRConfig` (ex.: política/enum ou boolean de cap), padrão = cap `1x`, comando `/oimr settings` e controle na `OIMRSettingsScreen`.
  - [x] Implementar deduplicação/cap por janela de dano ou tick de lava no servidor, respeitando afinidade atual.
  - [x] Adicionar chaves de tradução (`en_us.json` e `pt_br.json`) e GameTests comparando os dois modos com 2+ jogadores na lava.

---

### `BL-006` — Setting de respiração/fôlego subaquático para múltiplos membros (`1x` cap vs. `Nx` multiplicativo)
- **Prioridade:** P2 | **Esforço:** P
- **Comportamento desejado:** Nova opção de configuração com duas modalidades quando `N > 1` membros estão submersos simultaneamente:
  - **Cap em `1x` (padrão inicial):** o gasto de fôlego do grupo tem teto de `1 × fôlego gasto` por tick (como se apenas 1 pulmão estivesse sendo drenado por vez).
  - **Multiplicativo (até `N × fôlego gasto`):** se `N` membros estão submersos, o fôlego compartilhado se esgota até `N` vezes mais rápido.
- **Subtarefas:**
  - [x] Definir chave em `OIMRConfig` (padrão = cap `1x`), comando `/oimr settings` e controle na `OIMRSettingsScreen`.
  - [x] Integrar com a lógica de sincronização de `airSupply` e afogamento (incluindo interação com encantamento Respiração / efeito Water Breathing e afinidade).
  - [x] Adicionar traduções (`en_us.json` e `pt_br.json`) e GameTests nos dois modos.

---

### `BL-007` — Setting de danos ao longo do tempo (DoT) para múltiplos membros (`1x` cap vs. `Nx` multiplicativo)
- **Prioridade:** P2 | **Esforço:** P/M
- **Comportamento desejado:** Nova opção de configuração cobrindo fogo, Wither, Poison e outros danos contínuos (DoT) quando `N > 1` membros estão sofrendo o mesmo DoT ao mesmo tempo:
  - **Cap em `1x` (padrão inicial):** o dano aplicado ao grupo é limitado a `1 × dano` do DoT por ciclo, evitando multiplicação fatal em cadeia (especialmente quando o próprio mod propaga efeitos como Poison/Wither entre `N` membros!).
  - **Multiplicativo (`N × dano`):** cada membro afetado propaga seu tick de DoT para o grupo.
- **Observação de design:** Como o mod sincroniza efeitos (`Poison`, `Wither`) entre membros, sem cap `1x` o modo `Nx` amplifica drasticamente qualquer poção/ataque de Wither. Documentar essa interação claramente na tooltip da setting e avaliar se DoTs ambientais (fogo/cacto/magma) e DoTs de status compartilham a mesma setting ou subopções.
- **Subtarefas:**
  - [x] Catalogar fontes de DoT relevantes (fogo, fogo da alma, poison, wither, congelamento, etc.).
  - [x] Definir chave em `OIMRConfig` (padrão = cap `1x`), expor em `/oimr settings` e na `OIMRSettingsScreen`.
  - [x] Implementar controle de cap por tipo/fonte de DoT na janela de tick e validar interação com `syncEffects` e `affinity`.
  - [x] Adicionar traduções (`en_us.json` e `pt_br.json`) e GameTests.

---

## Épico 3 — Arquitetura, Persistência e Confiabilidade

### `BL-008` — Plano de refatoração do núcleo monolítico seguindo princípios SOLID
- **Prioridade:** P2 | **Esforço:** G | **Relação:** `TD-011` (`HiveMindManager.java` ~3.875 linhas, `HiveMind.java` ~938 linhas, `HiveMindGameTests.java` ~3.282 linhas)
- **Problema:** O núcleo concentra muitas responsabilidades em classes estáticas gigantes (`HiveMindManager`), misturando ciclo de vida de times, inventário, equipamentos, efeitos, dano/combate, morte/loot, teleporte/portais, áudio, progressão/avanços, rede e caches globais estáticos. Isso aumenta acoplamento, dificulta revisão/manutenção e eleva o risco de regressão ao implementar mecânicas novas (como `BL-001` a `BL-007`).
- **Diretrizes SOLID desejadas:**
  - **SRP (Responsabilidade Única):** separar serviços/domínios coesos (ex.: `InventorySyncService`, `VitalsAndDamageService`, `EffectSyncService`, `ProgressionService`, `DeathCascadeService`, `TeleportService`, `AudioSyncService`, `TeamLifecycleService`).
  - **OCP / DIP (Aberto-Fechado / Inversão de Dependência):** definir contratos/interfaces claros para políticas de dano/efeito/consumo e injetar contexto por instância de `MinecraftServer` (eliminando estado estático global mutável entre mundos/testes).
  - **ISP:** evitar interfaces ou acessores inchados passando apenas o recorte necessário para cada domínio.
- **Subtarefas:**
  - [x] Mapear todas as dependências e estado global atual em `HiveMindManager` e `HiveMind`.
  - [x] Desenhar a árvore de pacotes/serviços alvo e o plano de migração incremental **sem alterar formato de save** e mantendo os GameTests verdes a cada etapa (`HiveMindCoordinator` + `net.iakee.oneinventorymanyregrets.hivemind.service.*`).
  - [x] Encapsular caches/estados temporários em um coordenador por `MinecraftServer` (fim dos mapas estáticos globais vazando entre sessões).
  - [x] Dividir `HiveMindGameTests.java` em suítes menores por domínio (`InventoryIntegrityGameTests`, `CombatAndDeathGameTests`, `EffectsAndEnvironmentGameTests`, `ProgressionAndSocialGameTests`, `TeleportAndAudioGameTests`, `PersistenceAndConfigGameTests` + `HiveMindTestSupport`).

---

### `BL-009` — Expurgo e ciclo de vida de estados persistentes de equipes/jogadores órfãos
- **Prioridade:** P2 | **Esforço:** M/G | **Relação:** `TD-007`
- **Evidência confirmada:**
  - [HiveMindData.java](src/main/java/net/iakee/oneinventorymanyregrets/hivemind/HiveMindData.java#L38-L39) mantém `hiveMinds` e `personalInventories`.
  - `getOrCreate` e `getOrCreatePersonalState` ([HiveMindData.java](src/main/java/net/iakee/oneinventorymanyregrets/hivemind/HiveMindData.java#L66-L86)) apenas inserem entradas; `removePersonalState` ([HiveMindData.java](src/main/java/net/iakee/oneinventorymanyregrets/hivemind/HiveMindData.java#L88-L92)) não é chamado em lugar nenhum e não há remoção de hives.
  - `onEndServerTick` ([HiveMindManager.java](src/main/java/net/iakee/oneinventorymanyregrets/hivemind/HiveMindManager.java#L990)) percorre `data.getAll()` a cada tick.
- **Impacto:** Saves crescem indefinidamente com jogadores/equipes antigas; recriar uma equipe com o mesmo nome pode herdar inventário/estado antigo; custo do tick escala com hives órfãos.
- **Subtarefas:**
  - [x] Registrar metadado de última atividade (`lastActiveEpoch` / `lastActiveGameTime`) por hive e estado pessoal, preservando compatibilidade na leitura de saves antigos.
  - [x] Definir política segura de retenção (`TeamLifecycleService.CleanupPolicy`) e identificar equipes removidas/vazias (marcando times removidos como órfãos e reiniciando o estado caso um time com o mesmo nome seja recriado).
  - [x] Criar comando administrativo com modo `dry-run` / inspeção antes de expurgar definitivamente (`/oimr cleanup dry-run [retentionDays]` e `/oimr cleanup confirm [retentionDays]`).
  - [x] Indexar hives ativos/com membros online (`onlineMembersByHive` em `HiveMindCoordinator`) para que o tick não itere hives ociosos desnecessariamente.
  - [x] Cobrir com GameTests (remoção de equipe, recriação com mesmo nome, limpeza de estado pessoal após saída definitiva) e testes unitários JUnit (`CleanupPolicyTest`).

---

### `BL-010` — Gravação atômica da configuração e correção do fallback no reload
- **Prioridade:** P2 | **Esforço:** P/M | **Relação:** `TD-009`
- **Evidência confirmada:**
  - Em `OIMRConfig.load()` ([OIMRConfig.java](src/main/java/net/iakee/oneinventorymanyregrets/config/OIMRConfig.java#L79-L126)), uma `IOException` apenas registra `"Failed to load config at {}, using defaults"`, mas **não chama `applyDefaults()`**; num reload, os valores anteriores permanecem na memória apesar da mensagem afirmar o contrário.
  - Em `OIMRConfig.save()` ([OIMRConfig.java](src/main/java/net/iakee/oneinventorymanyregrets/config/OIMRConfig.java#L214-L251)), `Files.newOutputStream(path)` sobrescreve o arquivo diretamente sem arquivo temporário + movimentação atômica ou backup.
- **Impacto:** Operador é induzido a erro sobre o estado ativo no reload; falha ou queda durante escrita pode truncar/corromper `oneinventorymanyregrets.properties`.
- **Subtarefas:**
  - [x] Carregar e validar todas as propriedades em uma estrutura temporária (`ConfigSnapshot`) antes de aplicar em memória.
  - [x] Decidir e implementar política transparente de erro no reload (mantém os valores anteriores em memória no reload em runtime sem acionar transição de slots, com log e retorno explícito ao operador; no carregamento inicial aplica defaults reais).
  - [x] Gravar em arquivo temporário no mesmo diretório, garantir flush/`FileChannel.force(true)` e usar `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` com fallback seguro e preservação de `.bak`.
  - [x] Adicionar testes automatizados simulando arquivo inválido/corrompido e erro de leitura no reload (`OIMRConfigTest` e `PersistenceAndConfigGameTests`).

---

## Épico 4 — Testes, Traduções e Licenciamento

### `BL-011` — Documentação de suporte de idiomas, aviso de tradução por IA e cobertura de chaves
- **Prioridade:** P0 | **Esforço:** M | **Relação:** `TD-002`
- **Contexto atualizado:** apenas **Português Brasileiro (`pt_br.json`)** e **Inglês (`en_us.json`)** possuem revisão/suporte oficial (194 chaves cada). Os demais **143 arquivos** foram gerados por IA, não têm revisão humana/procedência confirmada, possuem 149 chaves e ficam fora do JAR; as 45 ausências estão catalogadas em `TRANSLATIONS.md`.
- **Subtarefas:**
  - [x] Deixar explícito na documentação (`README.md` e `TRANSLATIONS.md`) que apenas `en_us` e `pt_br` são oficialmente suportados e que os demais idiomas foram gerados por IA e não têm revisão garantida.
  - [x] Documentar formalmente as chaves que ainda precisam de tradução/tratamento:
    - `oimr.settings.random_death_loot`
    - `oimr.settings.random_death_loot.tooltip`
    - *(+ quaisquer novas chaves introduzidas pelas settings de `BL-004` a `BL-007`)*
  - [x] Decidir entre: (a) manter apenas `en_us` e `pt_br` empacotados na primeira release pública e manter traduções experimentais fora do artefato para revisão/contribuição, ou (b) completar, validar e rotular claramente a origem das traduções geradas por IA. **Decisão: (a), aplicada em `processResources`.**
  - [x] Criar script/checagem no CI que valide JSON válido, conjunto completo de chaves em relação a `en_us.json` e integridade dos placeholders (`%s`, `%1$s`, etc.).

---

### `BL-012` — Esclarecimento e regularização de escopo de licenciamento para terceiros
- **Prioridade:** P0 | **Esforço:** M | **Relação:** `TD-003`
- **O que significa o apontamento ("Conteúdo próprio mudou de CC0 para MIT, mas terceiros precisam de escopo explícito"):**
  - Anteriormente, o projeto usava `CC0-1.0` em `LICENSE` e `fabric.mod.json`. Na auditoria, o código/conteúdo criado pelo autor (Giordano Souza / `iakee`) foi alterado para a licença **MIT**.
  - Porém, declarar "o repositório inteiro é MIT" pode causar problema se existirem arquivos de **terceiros** ou de **origem não documentada** dentro do repositório — porque você não pode relicenciar sob MIT algo que não criou ou cuja licença original exige outro aviso.
  - No estado atual:
    1. O **Gradle Wrapper / scripts** possuem cabeçalhos **Apache-2.0** (já pertencem a terceiros e precisam manter seu escopo/aviso separado da MIT do mod).
    2. Os **143 arquivos de idioma novos** não têm procedência/autoria registrada no repositório.
    3. Os **PNGs** (`icon.png` e sprites de HUD/TAB) precisam ter confirmação de autoria própria (ou licença se houver base de terceiros).
- **Subtarefas:**
  - [ ] Confirmar e registrar a autoria dos assets gráficos (`icon.png`, sprites de `hud/` e `tab/`). **Inventário e commits registrados em `ASSET_PROVENANCE.md`; falta declaração explícita do titular, que não pode ser inferida do Git.**
  - [x] Criar um `THIRD_PARTY_NOTICES.md` listando exatamente o que é de terceiros e sob qual licença está (ex.: Gradle Wrapper sob Apache-2.0), deixando explícito que a MIT cobre apenas o conteúdo original do projeto.
  - [x] Alinhar a decisão das traduções (`BL-011`) com o licenciamento para não distribuir arquivos sem procedência clara em releases públicas.

---

### `BL-013` — Testes automatizados e validação manual de Cliente, Interface e Áudio
- **Prioridade:** P1 | **Esforço:** M/G | **Relação:** `TD-004` (`build.gradle:21`)
- **Contexto atualizado:** o projeto possui 15 Mixins de cliente (HUD/TAB, tela de configurações, áudio, fog e renderização). O Epic 4 habilitou Client GameTests e JUnit; a automação atual é uma barreira de fumaça, enquanto áudio, TAB, acessibilidade e multiplayer real permanecem na matriz manual.
- **Subtarefas:**
  - [x] Investigar viabilidade de habilitar `enableClientGameTests = true` no Gradle/CI (incluindo headless/Xvfb no Linux do GitHub Actions). **Habilitado com `runClientGameTest`, resolução 640×360 e job dedicado em Xvfb.**
  - [x] Adicionar testes unitários JUnit (`src/test`) para lógicas puras desacopladas do cliente/servidor (config, política de limpeza e estado/cálculo de afinidade do cliente).
  - [x] Criar um checklist/roteiro de testes manuais versionado (`docs/MANUAL_TEST_CHECKLIST.md`) cobrindo:
    - Tela `OIMRSettingsScreen` (singleplayer vs. multiplayer bloqueado/somente leitura, resoluções menores, teclado/narrador).
    - Overlay de HUD e painel TAB com indicadores de vínculo.
    - Áudio subaquático (`underwaterMuffle`) e sons de mobs (`sharedMobSounds` com direção e distância).
    - Multiplayer real: reconexão, servidor dedicado e comportamento com cliente vanilla sem o mod.

---

## Ordem Sugerida de Execução

1. **Bloqueadores de confiança e publicação (`P0`):**
   - `BL-011` (política e chaves de tradução) + `BL-012` (inventário/escopo de terceiros e `THIRD_PARTY_NOTICES.md`) — esforço documental/decisório rápido.
   - `BL-001` (fim da duplicação ao alterar sync de slots em runtime).
2. **Consistência de inventário e uso de itens (`P1`):**
   - `BL-002` (reserva determinística de flecha/item secundário) e `BL-003` (cooldown individual + consumo imediato do último arremessável).
3. **Base arquitetural antes de expandir mecânicas (`P2` estratégico):**
   - Iniciar `BL-008` (plano SOLID incremental, começando por isolar `OIMRConfig` / serviços de dano, efeitos e inventário) antes ou em paralelo a `BL-004`, para evitar que o sistema de efeitos replicados aumente ainda mais `HiveMindManager.java`.
   - Aproveitar a extração de configuração/persistência para fechar `BL-010` (config atômica) e `BL-009` (expurgo de hives órfãos).
4. **Novas mecânicas de balanceamento e cobertura de cliente:**
   - `BL-005`, `BL-006`, `BL-007` (settings de lava, respiração e DoT com cap `1x` por padrão).
   - `BL-004` (efeitos replicados individuais para afinidade parcial `0% < N < 100%`).
   - `BL-013` (testes de cliente/UI/áudio e validação multiplayer contínua).

---

## Pente Fino Pós-Implementação dos 4 Épicos (26/11/2026)

Auditoria realizada sobre o commit `5be8882` inspecionando código-fonte, mixins, configurações, testes unitários/GameTests e documentação. Abaixo estão todos os itens que **ainda precisam ser implementados, corrigidos ou concluídos**, separados por área.

### 1. Bloqueios e Pendências Críticas de Publicação (`P0` / `P1`)

- [ ] **`BL-012` — Confirmar titularidade/procedência dos 4 grupos de PNGs em `ASSET_PROVENANCE.md` (`P0`)**
  - Os 4 grupos (`icon.png`, `textures/gui/sprites/tab/*.png`, `textures/gui/sprites/hud/damage/*.png`, `textures/mob_effect/*.png`) ainda estão marcados como `confirmation required`. Falta a declaração explícita do titular (`Giordano Souza / iakee`).
- [ ] **`BL-013` — Executar e registrar a matriz manual em `docs/MANUAL_TEST_CHECKLIST.md` (`P1`)**
  - Todos os blocos manuais estão abertos: matriz Windows/Linux, `OIMRSettingsScreen` (multiplayer bloqueado, teclado/narrador, 640×360 e escalas de GUI), HUD/TAB, áudio subaquático e de mobs em fones/legendas, servidor dedicado com 2 clientes e teste com cliente vanilla.
- [x] **`BL-003` — Proteger consumíveis contínuos contra consumo duplo no mesmo tick da última unidade (`P1`)**
  - **Resolvido:** adicionado `InventoryAndAmmoService.shouldRejectEmptyOrStaleConsume` com guarda no `HEAD` de `ConsumableMixin.onConsume` e `InventoryAndAmmoService.shouldRejectStaleUseItemOn` no `HEAD` de `ServerPlayerGameModeMixin.useItemOn` (preservando interações de mão vazia em blocos).
- [ ] **Itens de publicação fora dos Épicos 1–4 (`TECHNICAL_DEBT.md`, `P1`/`P2` — dependem de decisão/execução do titular):**
  - `TD-006` (`P1`): corrigir DNS de `https://iakee.online` ou remover o campo `homepage` de `src/main/resources/fabric.mod.json`.
  - `TD-005` / `TD-014` / `TD-015` (`P1`/`P2`): decidir ícone final (256×256 atual), gerar screenshots reais (removendo placeholders do `README.md`), criar changelog/notas de versão, validar política da versão `1.0.0` e workflow de release.
  - `TD-010` (`P2`): montar matriz mínima de compatibilidade com outros mods sobre os 47 Mixins registrados em `oneinventorymanyregrets.mixins.json`.
  - `TD-012` (`P2`): avaliar substituição de `loom_version=1.19-SNAPSHOT` por versão estável, restringir faixa de `fabric-api` no `fabric.mod.json` e adicionar travas/verification-metadata.
  - `TD-013` (`P2`): esclarecer na UI do menu principal que alterações locais não afetam servidores dedicados e avaliar permissão configurável de operadores.
  - `TD-016` (`P3`): adicionar documentos de governança (`CONTRIBUTING.md`, `SECURITY.md`, templates de issue/PR).

### 2. Lacunas de Cobertura de Testes Automatizados (`P1` / `P2`)

- [x] **`BL-002` — Cobrir desconexão, morte e troca de equipe durante o *draw* em GameTest (`P1`)**
  - **Resolvido:** `InventoryIntegrityGameTests.testAmmoReservationReleasedOnDisconnectDeathAndTeamSwitch` valida reembolso na desconexão, devolução ao hive antigo na troca de equipe sem vazamento e liberação/drop único na morte.
- [x] **`BL-003` — Ampliar GameTest de arremessáveis e consumíveis contínuos (`P1`)**
  - **Resolvido:** `InventoryIntegrityGameTests.testMoreThrowablesContinuousConsumableAndUseItemOnRace` cobre `SPLASH_POTION`, `EXPERIENCE_BOTTLE`, `EGG`, corrida de `GOLDEN_APPLE` no mesmo tick e `useItemOn` com stack obsoleto.
- [x] **`BL-006` e `BL-007` — Cobrir afinidade parcial (`0% < N < 100%`) e Respiração nos GameTests de `underwaterBreathMode` e `dotDamageMode` (`P2`)**
  - **Resolvido:** `EffectsAndEnvironmentGameTests.testUnderwaterBreathModeCap1xVsMultiplicative` cobre Respiração III em `nx` e `DROWN` em `50%` de afinidade (`1x` vs `nx`); `CombatAndDeathGameTests.testDotDamageModeCap1xVsMultiplicative` cobre `DRAGON_BREATH`, `FREEZE` e `POISON` em `50%` de afinidade.
- [x] **`BL-013` (parte automatizável) — Expandir Client GameTests e testes unitários JUnit de cliente (`P1` / `P2`)**
  - **Resolvido:** `OIMRClientGameTests` agora valida acoplamento de controles (`syncHotbar`/`MainHand`, `excludeCreative`/`wipeCreative`, `sharedMobSounds`/filtro/volume), rolagem e rodapé em 640×360; `src/test/.../client/` ganhou `ClientHudFeedStateTest` (4 testes) e `ClientUnderwaterStateTest` (3 testes), totalizando 22 testes JUnit.
- [ ] **`BL-013` (HUD/TAB ao vivo) — Automatizar renderização/estado de `OIMRHudOverlay` e `PlayerTabOverlayMixin` com mundo carregado (`P2`)**
  - Ainda coberto apenas por inspeção e roteiro manual (`docs/MANUAL_TEST_CHECKLIST.md`).

### 3. Ajustes de Mecânica, Arquitetura (SOLID) e Confiabilidade (`P2` / `P3`)

- [x] **`BL-007` — Incluir `DamageTypes.DRAGON_BREATH` no catálogo de DoTs (`P2`)**
  - **Resolvido:** adicionado `HazardCapCategory.DRAGON_BREATH` em `HiveMind` e mapeado em `VitalsAndDamageService.resolveCappedHazardCategory`, com cobertura em GameTest.
- [ ] **`BL-001` — Definir comportamento esperado para buffs vanilla ativos ao desligar `syncEffects` em runtime (`P3`)**
  - Ao desativar `syncEffects`, `TeamLifecycleService.onConfigChanged` limpa `hive.getEffects()`, remainders e efeitos replicados, mas efeitos vanilla já aplicados permanecem individualmente em cada membro até expirar. Validar se o comportamento atual é desejado ou se deve haver remoção/aviso explícito.
- [ ] **`BL-008` — Fechar pendências SOLID restantes no núcleo (`P2`)**
  - [x] **ISP em `HiveMindContext.java`:** fatiado em sub-interfaces estreitas por domínio em `HiveMindContexts` (`InventoryAmmo`, `EffectSync`, `VitalsDamage`, `EnvironmentSync`, `ProgressionSocial`, `DeathCascade`, `TeleportAudio`, `TeamLifecycle`), aplicadas nos 8 serviços.
  - [ ] **Decomposição de `HiveMind.java` (1.102 linhas) e `OIMRConfig.java` (1.120 linhas):** separar cálculo/regras de estado em `HiveMind` dos codecs de persistência e desacoplar `OIMRConfig` de estado estático global mutável para facilitar isolamento entre testes/servidores.
  - [ ] **`TD-008` — Perfilagem de tick:** embora `onlineMembersByHive` tenha eliminado a iteração sobre hives ociosos, ainda falta perfilar (JFR/spark) o custo por tick de `broadcastChanges`, `detectEquipmentUpdates`, `flushDirty` e `lastSentInventoryStacks` com 10–50 jogadores.
- [ ] **`BL-009` / `BL-010` — Melhorias operacionais opcionais (`P3`)**
  - Avaliar limpeza automática periódica opcional (ou configurável) e geração de backup antes de `/oimr cleanup confirm` (`BL-009`), além de recuperação opcional via `.bak` quando a carga inicial de `OIMRConfig` detectar arquivo corrompido (`BL-010`).

### 4. Sincronização da Documentação Desatualizada (`P1` / `P2`)

- [x] **`README.md` (`P1`):**
  - **Resolvido:** aviso antigo de duplicação substituído pela documentação do drop canônico em runtime (`BL-001`), chaves `syncEffects`, `sharedMobSoundFilter` e `sharedMobSoundVolume` adicionadas à tabela de configuração e contagens de testes atualizadas.
- [x] **`TECHNICAL_DEBT.md` (`TD-001`, `TD-004`, `TD-007`, `TD-008`, `TD-009`, `TD-011`) (`P2`):**
  - **Resolvido:** itens atualizados com o estado real após os 4 épicos e o pente fino (mantendo em aberto apenas os residuais reais).
- [ ] **`BL-011` — Traduções experimentais (143 arquivos) (`P3` / longo prazo):**
  - Mantidos fora do JAR por decisão `(a)`; permanecem com 45 chaves faltantes (catalogadas em `TRANSLATIONS.md`) e sem procedência humana registrada.

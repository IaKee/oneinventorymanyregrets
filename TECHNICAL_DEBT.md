# Dívida técnica e preparação para publicação

Auditoria executada em **25/11/2026** sobre o checkout local de `OneInventoryManyRegrets`, sem commit, push, release ou publicação. Este documento separa fatos confirmados de sugestões e mantém os itens ordenados por prioridade.

## Escala usada

- **P0 / crítica:** bloqueia publicação responsável ou pode causar perda/duplicação relevante de dados.
- **P1 / alta:** deve ser resolvida antes de anunciar suporte público.
- **P2 / média:** risco real de manutenção, desempenho ou UX; pode entrar em uma versão inicial apenas com limitação explícita.
- **P3 / baixa:** melhoria desejável, sem bloqueio imediato.
- **Esforço:** `P` (até algumas horas), `M` (1–3 dias), `G` (vários dias ou mudança arquitetural).

## Resumo executivo

O estado atual **compila e passa todos os 47 GameTests obrigatórios** quando o Gradle roda em Java 25. A primeira tentativa com Java 21 falhou ainda na configuração do Loom, e a repetição com Eclipse Temurin 25 foi bem-sucedida. Não foram encontrados segredos óbvios, `TODO`, `FIXME`, `HACK`, stubs ou stack traces de depuração nos fontes auditados.

Mesmo assim, o checkout não está pronto para loja. O bloqueio mais grave é um comportamento de duplicação de itens explicitamente reconhecido quando a sincronização de slots é desligada. Também há 143 traduções novas sem procedência registrada e incompletas, ausência de teste de cliente, link de homepage quebrado, materiais de loja ausentes e riscos de compatibilidade/desempenho que ainda não foram medidos.

## Fatos confirmados — obrigatórios antes da publicação

### TD-001 — Opções de slot podem duplicar itens

- **Prioridade/severidade:** P0 / crítica.
- **Evidência:** `src/main/java/net/iakee/oneinventorymanyregrets/config/OIMRConfig.java:238-240` grava no próprio arquivo de configuração que desativar `syncHotbar`, `syncMainHand`, `syncOffHand` ou slots de armadura pode causar duplicação ativa; `src/main/resources/assets/oneinventorymanyregrets/lang/en_us.json:89-90` apresenta o mesmo comportamento como planejado. As opções ficam expostas na UI em `OIMRSettingsScreen.java:78-130`.
- **Impacto:** quebra economia e confiança do mundo, pode ser explorado e pode causar perda de estado ao entrar ou trocar de equipe. É incompatível com uma recomendação para servidores/mundos de produção.
- **Esforço:** G.
- **Dependências:** definir semântica de migração entre inventário compartilhado e pessoal; cobrir entrada, saída, troca de equipe, morte, respawn, contêiner aberto e jogadores offline.
- **Recomendação:** impedir combinações inseguras na versão pública ou implementar transferência transacional sem clonagem. Criar GameTests de propriedade/conservação de itens e testar upgrades de saves. Até lá, manter todas as opções `sync*` ativadas e exibir o aviso na página da loja.

### TD-002 — Traduções novas sem procedência e com cobertura incompleta

- **Prioridade/severidade:** P0 / alta jurídica e de publicação.
- **Evidência:** `git status --short` lista 143 JSONs de idioma não rastreados, além de alterações em `en_us.json` e `pt_br.json`. Todos os 145 JSONs analisados são sintaticamente válidos; `en_us` tem 151 chaves, enquanto os outros 143 idiomas têm 149 e não contêm `oimr.settings.random_death_loot` nem `oimr.settings.random_death_loot.tooltip`. A quantidade de argumentos `%s`/`%n$s` das chaves presentes é consistente em todos os idiomas, permitindo reordenação posicional legítima. Não existe script, cabeçalho, crédito ou documento indicando tradutor, fonte ou licença.
- **Impacto:** mensagens caem para inglês; qualidade e contexto não foram revisados; copiar/adaptar traduções de terceiros sem licença compatível pode impedir distribuição sob MIT.
- **Esforço:** G para revisão humana ampla; M para criar validação automatizada e limitar idiomas publicados.
- **Dependências:** decisão do titular sobre a origem; eventual autorização/licença dos tradutores; falantes/revisores por idioma.
- **Recomendação:** não empacotar os 143 arquivos na release até documentar a procedência. Preferir publicar somente idiomas revisados. Adicionar CI que valide JSON, conjunto de chaves e placeholders posicionais por multiconjunto (a ordem pode variar legitimamente entre idiomas).

### TD-003 — Conteúdo próprio mudou de CC0 para MIT, mas terceiros precisam de escopo explícito

- **Prioridade/severidade:** P0 / alta para licenciamento.
- **Evidência:** antes da auditoria, `LICENSE` continha CC0-1.0 e `fabric.mod.json:14` declarava `CC0-1.0`. O histórico registra Giordano Souza/IaKee com o mesmo e-mail e commits entre 2025 e 2026. O wrapper Gradle contém cabeçalhos Apache-2.0; as traduções novas não têm procedência.
- **Impacto:** uma declaração genérica de MIT pode ser interpretada como relicenciamento de conteúdo que o projeto talvez não controle.
- **Esforço:** P para o código próprio; M/G para inventário de terceiros.
- **Dependências:** confirmação de titularidade dos PNGs e traduções; decisão sobre eventual `THIRD_PARTY_NOTICES`.
- **Recomendação:** a auditoria atualizou `LICENSE` e o manifesto para MIT apenas para conteúdo próprio comprovado e registrou as exceções no README. Antes da release, documentar origem/licença de cada recurso não autoral e preservar avisos de terceiros. Não incluir conteúdo de procedência incerta.

### TD-004 — Cliente, interface e áudio não são testados automaticamente

- **Prioridade/severidade:** P1 / alta.
- **Evidência:** `build.gradle:21` define `enableClientGameTests = false`. O manifesto de Mixins registra oito Mixins de cliente, incluindo TAB, opções, áudio e fog; o task `test` retorna `NO-SOURCE`. Os 47 testes aprovados rodam no servidor GameTest.
- **Impacto:** regressões visuais, narrador/teclado, resoluções menores, payloads, desconexão e áudio podem chegar à loja apesar do build verde.
- **Esforço:** M/G.
- **Dependências:** infraestrutura de Client GameTest ou testes manuais reproduzíveis; capturas de referência.
- **Recomendação:** habilitar Client GameTests onde possível e criar um roteiro manual versionado para menu, TAB, telas, áudio subaquático, cliente modded, cliente sem mod, reconnect e servidor dedicado.

### TD-005 — Não há pacote de loja nem processo de release

- **Prioridade/severidade:** P1 / alta.
- **Evidência:** não havia README antes desta auditoria; continuam ausentes screenshots, changelog, notas de versão, metadados de Modrinth/CurseForge, workflow de release, checksums e política de suporte. `.github/workflows/build.yml` apenas executa `build` e envia `build/libs/` como artefato.
- **Impacto:** publicação manual e não reproduzível, risco de enviar o `sources.jar`, versão errada ou artefato sem validação final; página de loja incompleta.
- **Esforço:** M.
- **Dependências:** escolher lojas, identidade visual, política de versões e canais de suporte.
- **Recomendação:** produzir screenshots/descrição final, criar changelog, checklist de release e pipeline que publique somente o JAR remapeado após testes e inspeção. Não automatizar publicação antes de fechar P0/P1.

### TD-006 — Homepage declarada não resolve em DNS

- **Prioridade/severidade:** P1 / alta para apresentação; baixa para runtime.
- **Evidência:** `src/main/resources/fabric.mod.json:11` aponta para `https://iakee.online`. Em 25/11/2026, uma requisição HEAD falhou com `No such host is known`; a URL de fontes no GitHub respondeu HTTP 200.
- **Impacto:** link quebrado no menu de mods e em páginas que importem os metadados; reduz confiança e pode falhar em validações de loja.
- **Esforço:** P.
- **Dependências:** restaurar DNS/site ou decidir remover a homepage.
- **Recomendação:** corrigir o domínio e testar TLS/conteúdo ou remover o campo antes da release. Não substituir por URL inventada.

## Fatos confirmados — engenharia e operação

### TD-007 — Estados persistentes de equipes/jogadores não são expurgados

- **Prioridade/severidade:** P2 / média.
- **Evidência:** `HiveMindData.java:38-39` mantém mapas de hives e inventários pessoais; `getOrCreate`/`getOrCreatePersonalState` adicionam entradas em `HiveMindData.java:66-85`. Existe `removePersonalState` em `HiveMindData.java:88-92`, mas a busca global não encontra chamadas. Não existe remoção de hives. `HiveMindManager.java:973` percorre todos os hives a cada tick.
- **Impacto:** saves crescem com jogadores/equipes históricas; recriar uma equipe com o mesmo nome pode herdar estado antigo; custo do tick cresce com hives órfãos.
- **Esforço:** M/G.
- **Dependências:** política de retenção, identificação segura de equipe extinta, migração/backup de save.
- **Recomendação:** registrar última atividade, criar comando de inspeção/limpeza com dry-run e expurgar somente após backup e período de retenção. Testar recriação/renomeação de equipe.

### TD-008 — Sincronização por tick tem custo amplo e ainda não foi perfilada

- **Prioridade/severidade:** P2 / média, potencialmente alta em servidores grandes.
- **Evidência:** `HiveMindManager.java:945-1005` sincroniza equipes, percorre hives/membros e chama `broadcastChanges`, detecção de equipamento e `flushDirty` de avanços a cada tick. `getOnlineMembers` percorre toda a lista de jogadores (`HiveMindManager.java:306-313`) e é chamado repetidamente. Durante GameTest o servidor registrou `Can't keep up! ... 4569ms or 91 ticks behind` ao executar 47 testes simultâneos.
- **Impacto:** custo quadrático em alguns padrões de número de hives/jogadores e tráfego de inventário/progressão desnecessário. O aviso de GameTest é um sinal, não prova de lag em gameplay real.
- **Esforço:** G.
- **Dependências:** profiler (JFR/spark), cenários de 2/10/50 jogadores, métricas de pacotes e saves reais.
- **Recomendação:** perfilar antes de otimizar; indexar membros por hive, sincronizar por dirty flags/eventos e reduzir flush/broadcast por tick. Adicionar orçamento de desempenho em teste.

### TD-009 — Configuração não é gravada atomicamente e o fallback de reload é enganoso

- **Prioridade/severidade:** P2 / média.
- **Evidência:** em `OIMRConfig.java:77-123`, uma falha de leitura apenas registra “using defaults”, mas não chama `applyDefaults`; num reload, os valores anteriores permanecem. Em `OIMRConfig.java:210-245`, `Files.newOutputStream(path)` sobrescreve diretamente o arquivo, sem temporário + movimento atômico ou backup.
- **Impacto:** o operador pode acreditar que defaults foram aplicados quando não foram; queda durante escrita pode deixar arquivo parcial e comportamento imprevisível.
- **Esforço:** P/M.
- **Dependências:** definir política para valores inválidos/desconhecidos e compatibilidade de futuras chaves.
- **Recomendação:** carregar em estrutura temporária, validar tudo, aplicar de uma vez; em erro, aplicar defaults ou manter estado com mensagem verdadeira. Gravar em arquivo temporário, `fsync` quando aplicável e mover atomicamente, preservando backup.

### TD-010 — Superfície Mixin grande e compatibilidade não medida

- **Prioridade/severidade:** P2 / média-alta.
- **Evidência:** a varredura encontrou 122 anotações de injeção/wrap/modify nos Mixins. `oneinventorymanyregrets.mixins.json` usa `required: true` e `defaultRequire: 1`; qualquer alvo alterado pode impedir inicialização. Não há matriz de outros mods ou versões.
- **Impacto:** atualizações de Minecraft/Fabric e mods que alteram os mesmos métodos podem causar crash ou comportamento duplo.
- **Esforço:** G contínuo.
- **Dependências:** lista de mods-alvo e versões suportadas; ambiente automatizado de smoke test.
- **Recomendação:** documentar suporte estrito a 26.3, criar matriz mínima de compatibilidade e preferir APIs/eventos estáveis quando existirem. Para conflitos conhecidos, falhar com diagnóstico claro.

### TD-011 — Núcleo monolítico dificulta revisão e isolamento de regressões

- **Prioridade/severidade:** P2 / média.
- **Evidência:** `HiveMindManager.java` possui 3.811 linhas e concentra ciclo de vida, inventário, efeitos, dano, morte, teleporte, áudio, progressão, rede e caches globais. `HiveMind.java` possui 938 linhas; a suíte GameTest possui 3.282 linhas em uma única classe.
- **Impacto:** alto acoplamento, revisão difícil, risco de flags globais interferirem entre recursos e testes mais caros de manter.
- **Esforço:** G.
- **Dependências:** testes de caracterização e limites claros entre domínios.
- **Recomendação:** extrair serviços por domínio (inventário, vitais, progressão, morte, teleporte/áudio), encapsular estado por servidor e dividir testes por recurso sem alterar o formato de save de uma vez.

### TD-012 — Build depende de snapshot e não possui travas/verificação de dependências

- **Prioridade/severidade:** P2 / média.
- **Evidência:** `gradle.properties:9` usa `loom_version=1.19-SNAPSHOT`; não há lockfiles nem `gradle/verification-metadata.xml`. O CI usa actions por tag (`@v4`) e o manifesto aceita qualquer `fabric-api` com `"*"`, embora o build compile contra uma versão exata.
- **Impacto:** builds podem mudar ou quebrar sem alteração no repositório; risco de supply chain e suporte mais amplo do que foi testado.
- **Esforço:** P/M.
- **Dependências:** versão estável do Loom compatível; política de atualização.
- **Recomendação:** fixar Loom estável/imutável, habilitar dependency verification/locking, limitar a dependência runtime à faixa testada e considerar SHA imutável para Actions.

### TD-013 — UI de configuração pode induzir expectativas erradas fora do host integrado

- **Prioridade/severidade:** P2 / média de UX.
- **Evidência:** `OptionsScreenMixin.java:43-53` habilita a tela no menu principal e em servidor integrado, mas a desabilita conectado a multiplayer. `OIMRSettingsScreen.java:258-273` aplica a configuração localmente quando não existe servidor integrado. `OIMRCommand.java:125-139` restringe servidor dedicado a console/RCON, excluindo até operadores jogadores.
- **Impacto:** um jogador pode configurar no menu principal e esperar que isso altere um servidor dedicado; administradores precisam sair do jogo para usar console/RCON.
- **Esforço:** M.
- **Dependências:** protocolo seguro de configuração cliente-servidor e modelo de permissão.
- **Recomendação:** deixar explícito “configuração local/novo mundo” no menu principal, mostrar estado somente leitura em multiplayer e avaliar permissão de operador configurável no servidor.

### TD-014 — Versão `1.0.0` sugere estabilidade não demonstrada

- **Prioridade/severidade:** P2 / média de comunicação.
- **Evidência:** `gradle.properties:12` define `mod_version=1.0.0`, mas não há tags/releases/changelog nem testes de cliente/compatibilidade. O README agora marca pré-publicação.
- **Impacto:** usuários e lojas podem interpretar como versão estável e suportada.
- **Esforço:** P.
- **Dependências:** política SemVer e decisão do autor.
- **Recomendação:** antes da primeira publicação, escolher uma versão coerente (por exemplo, pré-release se ainda houver P0/P1), criar changelog e documentar suporte. Não alterar o número automaticamente sem decisão do titular.

## Recursos e arte

### TD-015 — Materiais visuais de loja ausentes e ícone precisa de decisão final

- **Prioridade/severidade:** P1 para publicação / P3 técnica.
- **Evidência:** não existem screenshots ou banner no repositório. `assets/oneinventorymanyregrets/icon.png` é um PNG 256×256 com fundo branco e lettering manual. Os três sprites pequenos de TAB existem e foram carregados visualmente sem corrupção aparente.
- **Impacto:** página de loja incompleta; o ícone pode não comunicar o conceito em tamanhos pequenos ou sobre temas variados.
- **Esforço:** M.
- **Dependências:** direção de arte e requisitos da loja escolhida.
- **Recomendação:** decidir conscientemente se o estilo atual é final; testar 32/64 px e fundo claro/escuro; criar banner e screenshots reais sem esconder limitações. Confirmar autoria/licença de todos os PNGs.

## Documentação e manutenção

### TD-016 — Documentos de governança ainda ausentes

- **Prioridade/severidade:** P3 / baixa antes de comunidade, média após publicação.
- **Evidência:** não há `CONTRIBUTING.md`, `SECURITY.md`, templates de issue/PR, `CODE_OF_CONDUCT.md` ou política de versões suportadas.
- **Impacto:** contribuições e relatos de vulnerabilidade chegam sem fluxo claro.
- **Esforço:** P/M.
- **Dependências:** canais reais de contato e disponibilidade do mantenedor.
- **Recomendação:** adicionar somente canais que serão monitorados; não criar e-mail, Discord ou SLA fictício.

### TD-017 — O diff de código atual falha na checagem de whitespace

- **Prioridade/severidade:** P3 / baixa.
- **Evidência:** `git diff --check` aponta whitespace no fim das linhas 8 e 17–20 de `OIMRClient.java`, todas pertencentes ao trabalho que já estava no checkout antes desta auditoria. A checagem restrita a `README.md`, `TECHNICAL_DEBT.md`, `LICENSE` e `fabric.mod.json` não aponta erros. `.gitattributes` define EOL apenas para `gradlew` e `*.bat`, e o Git emite avisos de conversão LF→CRLF em vários fontes.
- **Impacto:** ruído de revisão e impossibilidade de usar `git diff --check` como gate até corrigir o trecho; risco de diffs grandes por fim de linha.
- **Esforço:** P.
- **Dependências:** preservar o trabalho em andamento e escolher política de EOL do projeto.
- **Recomendação:** corrigir o whitespace junto da mudança proprietária que criou essas linhas e definir EOL explícito para Java/JSON/Markdown em `.gitattributes`, após conferir que isso não reformata o repositório inteiro.

## Sugestões opcionais (não bloqueiam sozinhas)

Estas são ideias, não fatos de defeito:

1. adicionar um comando administrativo de diagnóstico/exportação do hive, sem expor dados sensíveis de jogadores;
2. gerar relatório de tamanho do save, número de hives órfãos e volume de pacotes durante testes de carga;
3. adicionar Dependabot/Renovate após fixar uma política de versões;
4. gerar SBOM do artefato e checksums na release;
5. testar texto, contraste, navegação por teclado, narrador e escala de GUI como checklist de acessibilidade;
6. separar idiomas “revisados” de traduções experimentais e oferecer créditos por arquivo;
7. adicionar um mundo/demo ou vídeo curto somente depois de estabilizar a mecânica de inventário;
8. renomear internamente `comunism` para a grafia correta apenas com migração compatível da chave de configuração; revisar também o tom da piada para as regras das lojas-alvo.

## Itens resolvidos nesta auditoria

- criado este catálogo dedicado de dívida técnica;
- criado `README.md` de pré-publicação com instalação, uso, configuração, troubleshooting, limitações, roadmap, contribuição, créditos, licença e checklist;
- substituído o texto CC0 por MIT para conteúdo próprio comprovado: `Copyright (c) 2025-2026 Giordano Souza (iakee)`;
- alinhado `fabric.mod.json` de `CC0-1.0` para `MIT`;
- documentadas as exceções de terceiros e a pendência de procedência das traduções, sem relicenciamento silencioso;
- executado build completo com Java 25 e 47/47 GameTests aprovados;
- validada sintaxe dos 145 JSONs de idioma e identificadas as duas chaves ausentes em 143 arquivos;
- verificados os quatro PNGs visualmente; não foi alterado nenhum recurso;
- verificada ausência de segredos óbvios e marcadores `TODO/FIXME/HACK` na varredura textual.

## Comandos e resultados relevantes

```text
java -version
Resultado inicial: Java 21.0.4

.\gradlew.bat build --warning-mode all
Resultado inicial: FAIL — Fabric Loom requer runtime Java 25, mas o Gradle usou Java 21.

JAVA_HOME=<Eclipse Temurin 25.0.5.1> .\gradlew.bat build --warning-mode all
Resultado final: BUILD SUCCESSFUL; 47/47 required GameTests passed; test NO-SOURCE.

Validação PowerShell de JSON/chaves
Resultado: 145 arquivos válidos; en_us/pt_br com 151 chaves; 143 arquivos com 149, faltando as duas chaves de randomDeathLoot.

HEAD https://github.com/iakee/oneinventorymanyregrets
Resultado: HTTP 200.

HEAD https://iakee.online
Resultado: falha de DNS (No such host is known) em 25/11/2026.
```

## Ordem recomendada de execução

1. Fechar TD-001 (duplicação) e TD-002/TD-003 (direitos e traduções).
2. Cobrir cliente/multiplayer e compatibilidade (TD-004/TD-010).
3. Preparar materiais e release reproduzível (TD-005/TD-006/TD-012/TD-015).
4. Medir e corrigir persistência/desempenho (TD-007/TD-008/TD-009).
5. Refatorar e ampliar governança sem bloquear correções críticas (TD-011/TD-016).

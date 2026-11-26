# One Inventory, Many Regrets

> **Estado: pré-publicação.** O projeto possui testes unitários, GameTests de servidor, um Client GameTest de fumaça e validação automática de idiomas. Uma publicação responsável ainda depende da matriz manual de cliente/multiplayer e da confirmação de autoria/licença dos assets gráficos. Consulte [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md) e [docs/MANUAL_TEST_CHECKLIST.md](docs/MANUAL_TEST_CHECKLIST.md).

One Inventory, Many Regrets é um mod Fabric para Minecraft que transforma jogadores do mesmo grupo em uma mente coletiva. Por padrão, jogadores sem equipe pertencem ao grupo `default`; equipes do placar (`/team`) criam grupos separados. Inventário e vários estados de progressão são compartilhados, enquanto afinidade e opções de servidor controlam quanto dos efeitos, dano e recursos circula pelo grupo.

Este repositório ainda não representa uma versão publicada ou suportada em uma loja de mods. O valor `1.0.0` existe nos metadados de build, mas não há release, changelog ou matriz de compatibilidade que o valide como versão estável.

## Recursos implementados

- agrupamento automático por equipes vanilla do placar, com um grupo padrão para jogadores sem equipe;
- inventário, equipamentos, Ender Chest, fome, experiência, receitas, avanços, semente de encantamento e ponto de renascimento compartilhados;
- saúde, absorção, efeitos, dano ambiental e morte coletiva em afinidade total;
- escalonamento configurável de afinidade, fome e experiência;
- opções para sincronizar ou separar hotbar, mãos e cada slot de armadura;
- compartilhamento configurável de knockback, pérolas do End, portais, abafamento subaquático e sons de criaturas;
- animais domesticados amigáveis entre integrantes do mesmo grupo;
- mensagens de morte próprias e fallback localizado enviado pelo servidor;
- painel no TAB com indicadores de vínculo e tela de opções para o host de mundos integrados;
- comandos de status, listagem e administração das configurações.

A suíte automatizada cobre cenários de inventário, combate, efeitos, fome, experiência, progressão, morte, teleporte, sons, persistência e configurações. Isso não substitui testes manuais de cliente, multiplayer real ou compatibilidade com outros mods.

## Screenshots

> **PLACEHOLDER — tela de configurações:** capturar a tela completa com as seções de afinidade, slots, combate e áudio.

> **PLACEHOLDER — HUD/TAB:** capturar o painel TAB com o indicador de grupo e as porcentagens de vínculo.

> **PLACEHOLDER — gameplay:** mostrar dois ou mais jogadores compartilhando inventário ou sofrendo um evento coletivo.

Não publique estes placeholders como imagens finais da página da loja.

## Requisitos e compatibilidade

| Componente | Estado verificado neste checkout |
| --- | --- |
| Minecraft | `26.3` |
| Fabric Loader | `0.20.1` ou superior, conforme o manifesto |
| Fabric API | build testado com `0.166.2+26.3` |
| Java | JDK/JRE `25` |
| Ambiente | código comum e cliente; instalação nos dois lados é a opção recomendada |

O build foi validado isoladamente com Java 25, Minecraft 26.3, Fabric Loader 0.20.1 e Fabric API 0.166.2+26.3. A conexão de clientes sem o mod possui proteções no código (payloads condicionais e texto de fallback), mas **a compatibilidade com cliente vanilla ainda não foi validada manualmente** e não deve ser anunciada como suporte confirmado.

Não há matriz de compatibilidade com outros mods. A implementação usa muitos Mixins em inventário, entidades, combate, progressão e interface; conflitos são possíveis.

## Instalação

1. Faça backup do mundo e dos inventários dos jogadores. O mod altera estado persistente compartilhado e regras de morte.
2. Instale Fabric Loader compatível com Minecraft 26.3.
3. Coloque Fabric API compatível na pasta `mods`.
4. Coloque `oneinventorymanyregrets-1.0.0.jar` na pasta `mods`. Não use o arquivo `-sources.jar`.
5. Inicie o jogo/servidor e confira o log por falhas de Mixin ou configuração.

Até que os bloqueios de publicação sejam resolvidos, gere o JAR localmente em vez de redistribuir um artefato deste checkout.

## Uso

Jogadores sem equipe vanilla entram no grupo `default`. Para separar grupos, use as equipes do placar, por exemplo:

```mcfunction
/team add red
/team join red PlayerName
```

Mover um jogador entre equipes muda o hive ativo e pode substituir/sincronizar seu estado. Teste primeiro em uma cópia do mundo.

Comandos disponíveis:

```text
/oimr status
/oimr list
/oimr settings
/oimr settings <opção> <valor>
/oimr settings reload
/oimr cleanup dry-run [dias]
/oimr cleanup confirm [dias]
```

`status` e `list` são informativos. `settings` e `cleanup` são restritos ao host de Singleplayer/LAN ou ao console/RCON de servidor dedicado; jogadores operadores em um servidor dedicado não recebem essa permissão pelo comando atual. `cleanup dry-run` lista grupos de equipes removidas/órfãs, equipes vazias inativas e estados pessoais obsoletos elegíveis sem modificar o save, enquanto `cleanup confirm` executa o expurgo (padrão: `0` dias para equipes já removidas do placar e `30` dias de inatividade para equipes vazias existentes e estados pessoais não vazios).

## Configuração

O arquivo é criado em `config/oneinventorymanyregrets.properties`. No cliente, a tela fica nas opções do jogo; durante uma partida ela só fica ativa para um servidor integrado. Em servidor dedicado, use o arquivo e o console/RCON.

| Chave | Padrão | Função resumida |
| --- | ---: | --- |
| `affinity` | `100` | intensidade do vínculo, de 0 a 100% |
| `hungerWeight` | `1.0` | peso da fome compartilhada |
| `xpWeight` | `1.0` | peso da experiência compartilhada |
| `sharedKnockback` | `true` | compartilha knockback |
| `sharedEnderPearl` | `true` | compartilha teleporte por pérola |
| `sharedPortals` | `true` | compartilha portais |
| `underwaterMuffle` | `true` | sincroniza o abafamento subaquático |
| `sharedMobSounds` | `true` | replica sons de criaturas para o grupo |
| `sharedMobSoundFilter` | `all` | filtra canais propagados (`all`, `hostile`, `neutral`, `none`) |
| `sharedMobSoundVolume` | `100` | escala de volume (`0`–`100%`) dos sons replicados antes da Afinidade |
| `friendlyPets` | `true` | evita hostilidade entre pets aliados |
| `excludeCreativeFromDeath` | `true` | exclui criativos da cascata de morte |
| `wipeCreativeInventoryOnDeath` | `true` | controla limpeza de inventário criativo na morte coletiva |
| `randomDeathLoot` | `true` | sorteia entre os mortos o local dos drops da morte coletiva |
| `lavaDamageMode` | `1x` | controla dano de lava com múltiplos membros (`1x` cap ou `nx` multiplicativo) |
| `underwaterBreathMode` | `1x` | controla gasto de fôlego subaquático com múltiplos membros (`1x` cap ou `nx` multiplicativo) |
| `dotDamageMode` | `1x` | controla danos contínuos (fogo, poison, wither, congelamento, cacto, bagas, bafo do dragão) entre múltiplos membros (`1x` cap ou `nx` multiplicativo) |
| `comunism` | `false` | usa uma mensagem humorística coletiva ao morrer de fome |
| `syncEffects` | `true` | sincroniza efeitos de status com duração entre os membros do grupo |
| `syncHotbar` | `true` | sincroniza a hotbar |
| `syncMainHand` | `true` | sincroniza a mão principal quando a hotbar não força essa sincronização |
| `syncOffHand` | `true` | sincroniza a mão secundária |
| `syncHelmet` | `true` | sincroniza capacete |
| `syncChestplate` | `true` | sincroniza peitoral |
| `syncLeggings` | `true` | sincroniza calças |
| `syncBoots` | `true` | sincroniza botas |

> **Observação sobre desligamento de slots em runtime:** ao desativar a sincronização de qualquer slot da hotbar ou de equipamento em tempo real, o servidor cancela usos ativos da mão afetada, remove o item atualmente compartilhado e o dropa no mundo (junto ao operador ou membro prioritário do grupo, ou na fila do próximo login se o grupo estiver offline) antes de liberar os slots individuais — evitando duplicação entre integrantes.

### Efeitos replicados em afinidade parcial (`0% < affinity < 100%`)

Quando a afinidade está entre `1%` e `99%`, efeitos puramente numéricos/periódicos (como Velocidade, Força, Veneno, Absorção e Vida Extra) escalam por duração ou valor numérico, enquanto efeitos mecânicos/binários vanilla são transmitidos aos colegas usando efeitos replicados dedicados do próprio mod com duração integral e intensidade proporcional à afinidade:

| Efeito original | Efeito replicado (`oneinventorymanyregrets:*`) | Comportamento parcial |
| --- | --- | --- |
| Invisibilidade | `translucency` (Translucidez) | reduz o raio de detecção de mobs e aplica semitransparência visual proporcionalmente à afinidade |
| Visão Noturna | `twilight_vision` (Visão Crepuscular) | clareia parcialmente a iluminação e névoa proporcionalmente à afinidade |
| Respiração Aquática | `aquatic_respiration` (Respiração Aquática Parcial) | preserva o fôlego com chance igual à afinidade a cada tick |
| Queda Lenta | `cushioned_fall` (Queda Amortecida) | interpola a gravidade de queda e reduz dano de queda pela afinidade |
| Brilho | `faint_glow` (Brilho Tênue) | pulsa o contorno brilhante em cadência proporcional à afinidade |
| Resistência ao Fogo | `ember_ward` (Proteção Térmica) | reduz dano de fogo/lava e expande visibilidade na lava pela afinidade |
| Levitação | `partial_levitation` (Flutuação Parcial) | aplica empuxo vertical parcial e reduz dano de queda pela afinidade |
| Graça do Golfinho | `fluid_grace` (Graça Fluida) | acelera o nado proporcionalmente à afinidade |
| Poder do Conduto | `conduit_echo` (Eco do Conduto) | combina preservação de ar, visão subaquática e mineração parcial |
| Fôlego de Náutilo | `nautilus_echo` (Fôlego de Náutilo Parcial) | preserva o ar submerso com chance igual à afinidade |

Para efeitos numéricos/de atributo (`Speed`, `Slowness`, `Haste`, `Mining Fatigue`, `Strength`, `Weakness`, `Jump Boost`, `Resistance`, `Luck`, `Unluck`), o mod aplica interpolação **grosso + fino (5:1)**: cada nível vanilla equivale a **5 níveis** da contraparte fracionária (`fast_walking`, `heavy_step`, `nimble_hands`, `weary_hands`, `might_surge`, `feeble_grip`, `spring_step`, `warding_aura`, `minor_fortune`, `minor_jinx`). Por exemplo, `Speed II` a `60%` de afinidade gera `Speed I` (ajuste grosso) + `Fast Walking I` (ajuste fino, total `+24%`); a cada `5` níveis finos acumulados ocorre a promoção automática para `1` nível completo do efeito original. Abaixo de `1` unidade fina, o nível fino I acumula duração escalada.

Efeitos de outros mods ou não mapeados utilizam fallback determinístico de escala de duração (`duração × afinidade`) com acumulador de resto fracionário e amplificador preservado. Ao alterar a afinidade para `100%` ou `0%`, desativar `syncEffects` ou sair do grupo, os efeitos replicados são removidos automaticamente.

## Build e testes para contribuidores

Pré-requisitos: Git e JDK 25. Confirme que o Gradle está usando Java 25; ter apenas uma toolchain configurada não basta, pois a versão atual do Fabric Loom já exige que o próprio Gradle rode em Java 25.

No Windows:

```powershell
$env:JAVA_HOME = 'C:\caminho\para\jdk-25'
./gradlew.bat build
```

Linux/macOS:

```bash
export JAVA_HOME=/caminho/para/jdk-25
./gradlew build
```

O task `build` executa os testes unitários e os GameTests de servidor configurados. O Client GameTest possui task própria porque abre o cliente gráfico:

```text
node scripts/validate-translations.mjs
./gradlew test
./gradlew runGameTest
./gradlew runClientGameTest
```

No Linux, o CI executa o último comando com `xvfb-run` e renderização por software. O teste de cliente abre `OIMRSettingsScreen` em 640×360, verifica título narrável, acoplamento dos controles (`syncHotbar`/`MainHand`, `excludeCreative`/`wipeCreative`, `sharedMobSounds`/filtro/volume), rolagem, rodapé e defaults seguros de estado do cliente. Ele complementa os testes unitários e não substitui o roteiro manual de tela, HUD, TAB, áudio e multiplayer em [docs/MANUAL_TEST_CHECKLIST.md](docs/MANUAL_TEST_CHECKLIST.md).

Artefatos esperados:

```text
build/libs/oneinventorymanyregrets-1.0.0.jar
build/libs/oneinventorymanyregrets-1.0.0-sources.jar
```

## Solução de problemas

### “Dependency requires at least JVM runtime version 25”

O Gradle foi iniciado com Java 21 ou anterior. Aponte `JAVA_HOME` para um JDK 25 e repita o build.

### Falha de Mixin ao iniciar

Confirme exatamente as versões da tabela acima e teste sem outros mods além de Fabric API. Depois reintroduza os demais mods um a um para identificar o conflito.

### Configuração não muda em servidor dedicado

Edite `config/oneinventorymanyregrets.properties` e execute `/oimr settings reload` pelo console/RCON. O comando de jogador é deliberadamente bloqueado em servidor dedicado, inclusive para operadores.

### Itens mudaram ou foram dropados após desligar `sync*` ou trocar de equipe

Ao desativar `syncHotbar`, `syncMainHand`, `syncOffHand` ou slots de armadura em runtime, o mod dropa no chão os itens atualmente compartilhados daqueles slots antes de reativar o armazenamento individual por jogador. Ao trocar de equipe (`/team`), o inventário compartilhado anterior permanece na equipe de origem e o jogador passa a usar o estado da equipe de destino (e qualquer munição reservada em *draw* é devolvida ao grupo anterior).

### Texto em inglês em outro idioma

Releases incluem somente `en_us` e `pt_br`, os dois idiomas oficialmente suportados. Os outros 143 JSONs são rascunhos experimentais gerados por IA, sem revisão humana garantida, e não são empacotados. A política, a fila de 45 chaves ausentes e o processo de contribuição estão em [TRANSLATIONS.md](TRANSLATIONS.md).

## Limitações conhecidas

- a cobertura automatizada de cliente valida a tela de opções (`OIMRSettingsScreen` em 640×360, acoplamento de controles e rodapé) e estados puros de cliente em JUnit; HUD, TAB, áudio e variações visuais ainda exigem validação manual;
- clientes vanilla e combinações com outros mods ainda não foram validados;
- os 143 rascunhos de idioma gerados por IA não são distribuídos e ainda precisam de revisão/procedência para futura promoção;
- a autoria/origem dos PNGs está inventariada, mas aguarda declaração explícita do titular;
- hives e inventários pessoais persistidos não são expurgados automaticamente (o expurgo é manual via `/oimr cleanup dry-run|confirm`);
- a homepage declarada nos metadados (`iakee.online`) não resolveu em DNS durante esta auditoria;
- não há screenshots finais, changelog, notas de versão nem automação de publicação;
- o ícone atual é um PNG 256×256 com fundo branco e lettering manual; sua adequação visual à loja ainda precisa de decisão do autor.

## Roadmap resumido

1. Concluir a declaração de titularidade/procedência dos PNGs em [ASSET_PROVENANCE.md](ASSET_PROVENANCE.md).
2. Executar a matriz manual de cliente, áudio e multiplayer real ([docs/MANUAL_TEST_CHECKLIST.md](docs/MANUAL_TEST_CHECKLIST.md)) e validar compatibilidade com cliente vanilla/mods populares.
3. Obter revisão humana antes de promover qualquer idioma experimental.
4. Perfilar desempenho com grupos maiores (10–50 jogadores) e concluir a decomposição SOLID de `HiveMind`/`OIMRConfig`.
5. Produzir screenshots, ícone final, changelog e pipeline de release reproduzível.

O backlog completo, com prioridade, evidência e esforço, está em [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md).

## Contribuição

Antes de enviar uma alteração:

1. abra uma issue ou descreva claramente o problema e o cenário;
2. mantenha mudanças focadas e preserve compatibilidade de saves;
3. execute `./gradlew build` com Java 25;
4. inclua GameTests para comportamento de servidor e Client GameTests para fluxos visuais automatizáveis;
5. atualize documentação e traduções afetadas e rode `node scripts/validate-translations.mjs`.

Código-fonte: <https://github.com/iakee/oneinventorymanyregrets>

## Créditos

- autoria do mod e histórico do repositório: Giordano Souza (`iakee`);
- Fabric Loader, Fabric API, Fabric Loom, Gradle e Minecraft pertencem aos respectivos projetos e titulares;
- traduções e recursos adicionais só devem ser creditados após a procedência ser confirmada; consulte [TRANSLATIONS.md](TRANSLATIONS.md) e [ASSET_PROVENANCE.md](ASSET_PROVENANCE.md).

## Licença

O código e o conteúdo original de titularidade do projeto estão disponíveis sob a [MIT License](LICENSE), copyright 2025–2026 Giordano Souza (`iakee`). O manifesto Fabric foi alinhado para `MIT`.

Essa declaração não relicencia componentes de terceiros. Os scripts e o wrapper Gradle mantêm os avisos Apache-2.0 presentes nos próprios arquivos. As 143 traduções experimentais sem procedência documentada não são tratadas como MIT nem incluídas no JAR. O inventário de exceções está em [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), também incluído no artefato.

## Checklist de publicação

- [x] Build reproduzido localmente com Java 25.
- [x] 62 GameTests obrigatórios, 22 testes JUnit e 1 Client GameTest aprovados.
- [x] README de pré-publicação criado.
- [x] `LICENSE` e `fabric.mod.json` alinhados para MIT no conteúdo próprio.
- [x] URL do código-fonte verificada.
- [x] Publicar apenas `en_us` e `pt_br`; validar JSON, cobertura e placeholders no CI.
- [x] Registrar escopo da MIT e avisos de terceiros.
- [x] Adicionar testes unitários e Client GameTest em CI/Xvfb.
- [ ] Confirmar direitos e origem dos PNGs inventariados.
- [ ] Executar e registrar o roteiro manual de cliente/multiplayer real.
- [ ] Validar cliente sem mod e uma matriz de compatibilidade.
- [ ] Corrigir ou remover a homepage sem DNS.
- [ ] Criar screenshots e decidir o ícone final.
- [ ] Criar changelog, notas de versão e política de suporte.
- [ ] Fixar dependências de build reproduzíveis e revisar supply chain.
- [ ] Gerar um JAR limpo, conferir seu conteúdo e testar instalação do artefato final.
- [ ] Publicar somente após revisão manual deste checklist.

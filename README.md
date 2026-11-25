# One Inventory, Many Regrets

> **Estado: pré-publicação.** O mod compila e os 47 GameTests atuais passam, mas ainda existem bloqueios documentados antes de uma publicação responsável em loja. Em especial: sincronizações de slot desativadas podem duplicar itens, os testes automatizados não cobrem o cliente e as traduções novas precisam de revisão de origem e qualidade. Consulte [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md).

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
```

`status` e `list` são informativos. A edição de configurações é restrita ao host de Singleplayer/LAN ou ao console/RCON de servidor dedicado; jogadores operadores em um servidor dedicado não recebem essa permissão pelo comando atual.

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
| `friendlyPets` | `true` | evita hostilidade entre pets aliados |
| `excludeCreativeFromDeath` | `true` | exclui criativos da cascata de morte |
| `wipeCreativeInventoryOnDeath` | `true` | controla limpeza de inventário criativo na morte coletiva |
| `randomDeathLoot` | `true` | sorteia entre os mortos o local dos drops da morte coletiva |
| `comunism` | `false` | usa uma mensagem humorística coletiva ao morrer de fome |
| `syncHotbar` | `true` | sincroniza a hotbar |
| `syncMainHand` | `true` | sincroniza a mão principal quando a hotbar não força essa sincronização |
| `syncOffHand` | `true` | sincroniza a mão secundária |
| `syncHelmet` | `true` | sincroniza capacete |
| `syncChestplate` | `true` | sincroniza peitoral |
| `syncLeggings` | `true` | sincroniza calças |
| `syncBoots` | `true` | sincroniza botas |

> **Aviso importante:** o próprio código identifica que desativar a sincronização de hotbar/equipamentos pode causar duplicação ativa de itens ao entrar, trocar de equipe ou mover itens. Mantenha todas as sincronizações ligadas em mundos importantes até esse comportamento ser redesenhado.

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

O task `build` executa o GameTest configurado. Resultado desta auditoria em 25/11/2026: build bem-sucedido e **47/47 GameTests obrigatórios aprovados**. Não há testes unitários tradicionais (`test` informa `NO-SOURCE`) e os Client GameTests estão desativados.

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

### Itens duplicaram ou mudaram após troca de equipe

Restaure um backup se necessário e reative todas as opções `sync*`. Esse risco é conhecido e ainda bloqueia a recomendação do mod para mundos de produção.

### Texto em inglês em outro idioma

O fallback para inglês é esperado quando uma chave não existe. As traduções novas ainda aguardam revisão; duas chaves de `randomDeathLoot` estão ausentes em 143 idiomas neste checkout.

## Limitações conhecidas

- desativar sincronização de slots pode duplicar itens;
- o cliente, a tela de opções, o TAB e o áudio não possuem testes automatizados ativos;
- clientes vanilla e combinações com outros mods ainda não foram validados;
- 143 arquivos de idioma novos precisam de procedência documentada e duas chaves novas;
- hives e inventários pessoais persistidos não são expurgados automaticamente;
- a homepage declarada nos metadados (`iakee.online`) não resolveu em DNS durante esta auditoria;
- não há screenshots finais, changelog, notas de versão nem automação de publicação;
- o ícone atual é um PNG 256×256 com fundo branco e lettering manual; sua adequação visual à loja ainda precisa de decisão do autor.

## Roadmap resumido

1. Eliminar a duplicação de itens ou remover da versão pública as combinações inseguras de sincronização.
2. Validar procedência, placeholders e cobertura das traduções.
3. Ativar testes de cliente e executar multiplayer real, cliente sem mod e matriz de mods populares.
4. Medir desempenho com grupos maiores e implementar limpeza de hives/estados órfãos.
5. Produzir screenshots, ícone final, changelog e pipeline de release reproduzível.

O backlog completo, com prioridade, evidência e esforço, está em [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md).

## Contribuição

Antes de enviar uma alteração:

1. abra uma issue ou descreva claramente o problema e o cenário;
2. mantenha mudanças focadas e preserve compatibilidade de saves;
3. execute `./gradlew build` com Java 25;
4. inclua GameTests para comportamento de servidor e, quando a infraestrutura existir, testes de cliente;
5. atualize documentação e traduções afetadas.

Código-fonte: <https://github.com/iakee/oneinventorymanyregrets>

## Créditos

- autoria do mod e histórico do repositório: Giordano Souza (`iakee`);
- Fabric Loader, Fabric API, Fabric Loom, Gradle e Minecraft pertencem aos respectivos projetos e titulares;
- traduções e recursos adicionais só devem ser creditados após a procedência ser confirmada.

## Licença

O código e o conteúdo original de titularidade do projeto estão disponíveis sob a [MIT License](LICENSE), copyright 2025–2026 Giordano Souza (`iakee`). O manifesto Fabric foi alinhado para `MIT`.

Essa declaração não relicencia componentes de terceiros. Os scripts e o wrapper Gradle mantêm os avisos Apache-2.0 presentes nos próprios arquivos. As 143 traduções novas sem procedência documentada **não devem ser tratadas como liberadas sob MIT nem incluídas em publicação** até revisão do titular/origem.

## Checklist de publicação

- [x] Build reproduzido localmente com Java 25.
- [x] 47 GameTests obrigatórios aprovados.
- [x] README de pré-publicação criado.
- [x] `LICENSE` e `fabric.mod.json` alinhados para MIT no conteúdo próprio.
- [x] URL do código-fonte verificada.
- [ ] Resolver ou retirar as opções que permitem duplicação de itens.
- [ ] Confirmar direitos, créditos e qualidade de todas as traduções e imagens.
- [ ] Completar as duas chaves ausentes nos 143 idiomas novos.
- [ ] Adicionar e executar testes de cliente/multiplayer real.
- [ ] Validar cliente sem mod e uma matriz de compatibilidade.
- [ ] Corrigir ou remover a homepage sem DNS.
- [ ] Criar screenshots e decidir o ícone final.
- [ ] Criar changelog, notas de versão e política de suporte.
- [ ] Fixar dependências de build reproduzíveis e revisar supply chain.
- [ ] Gerar um JAR limpo, conferir seu conteúdo e testar instalação do artefato final.
- [ ] Publicar somente após revisão manual deste checklist.

# Notas do port Essence → High Five

Documento técnico da adaptação: o que mudou em relação à versão Essence e o que foi adicionado neste port.

---

## 1. Mapeamento de API Essence (RoseVain) → High Five (CT 2.6)

Os paths `org.l2jmobius.*` são idênticos entre as crônicas (o Mobius unificou o naming), então a maior parte compilou direto. As mudanças necessárias foram de API/semântica:

| Essence | High Five | Nota |
|---|---|---|
| `setReputation(-X)` | `setKarma(+X)` | **Sinal invertido**: em HF o karma é positivo para PK. |
| `broadcastReputation()` | `broadcastKarma()` | |
| `chargeShot(type)` | `setChargedShot(type, true)` | |
| `doAutoAttack(target)` | `getAI().setIntention(Intention.ATTACK, target)` | O `doAttack` de HF é **um único golpe**, não um auto-ataque contínuo. A intenção ATTACK mantém o ciclo e a aproximação. |
| `getInstanceWorld()` / `setInstance()` | `getInstanceId()` / `setInstanceId(int)` | HF usa ids `int`, não o objeto `Instance`. |
| `PlayerClass.KAMAEL_SOLDIER` | `MALE_SOLDIER` / `FEMALE_SOLDIER` | Em HF as classes base Kamael têm gênero fixo (o sexo do appearance é forçado conforme a classe). |
| `Race.SYLPH`, `Race.HIGH_ELF`, `SYLPH_GUNNER` | *(removidos)* | Não existem em HF; fora do roster de criação. |

## 2. Armadilhas de players client-less resolvidas (além do mapeamento)

Coisas que "compilavam" mas falhavam em runtime, além da mudança de API:

- **Idioma nulo + multilang**: sem cliente ninguém define `_lang`; com multilang ativo, `sendDamageMessage` estourava com NPE em `NpcNameLocalisationData` e **abortava a cadeia de dano** (batiam sem tirar vida nem gerar aggro). Fix: `setLang(default)` ao preparar o phantom.
- **IDs de items entre crônicas**: os IDs de equipamento de Essence apontavam para **items diferentes** em HF (mages com dois elmos e sem roupa, armadura grade A no nível 20, EtcItems não-equipáveis como "armas"). Todos os packs foram reconstruídos com IDs **verificados um a um** contra `data/stats/items` de HF.
- **Formato de spawns HF**: os spawns territoriais (`<spawn zone=>`) não trazem coordenadas por-npc; são resolvidos ao centroide do polígono `<territory>`. Um parser de Essence carregava 0 spots sem dar erro.
- **Skills de summon**: não se detectam por nome ("Summon Treasure Key"/"Siege Golem" também começam com "Summon"); filtram-se pela classe do efeito `Summon` real.
- **Servitor + login**: a restauração do servitor vive em `EnterWorld` (cliente); sem ela, a entrada de `character_summons` do logout anterior bloqueia o `canSummon` para sempre. Os phantoms a executam ao logar.
- **`online` no banco**: `isOnlineInt()` retorna 0 sem cliente e o autosave o reescreve; para que a web conte os bots, força-se `online=1` ao logar e reafirma-se a cada tick do gerenciador (toggle configurável).

Os três patches de `core-patches/` são a parte disto que não dá para resolver pelo datapack.

## 3. Recursos originais do autor vs. adicionados no port

**Sistema original (MiaCodeWEB), conservado e adaptado a HF:**
- Arquitetura modular (Phantom* separados), criação client-less, farm em spots reais do datapack, spawn com validação de geodata, metas de nível com descanso em cidade, MP-rest para mages, packs de equipamento por grade com variantes, shots automáticos, limpeza de inventário, PvP/PK, painel GM `.pmenu` + bypass, chat opcional (Gemini).

**Adicionado no port (Rekiem):**
- **Gerenciador de população autônomo**: curva de 4 faixas horárias editáveis (% do pool, persistidas em `config/Custom/PhantomAI.ini`), sessões de 2–5h com rotação gradual, auto-start no boot (sem `.pstart` manual após reiniciar).
- **Pirâmide de níveis** ao criar (população espalhada pelo mapa) + faixa de nível manual.
- **Comportamento humano**: poções de loja usadas em combate, fuga com HP crítico, sentar para regenerar com decisão imediata lutar-ou-fugir ao ser atacado, mages que priorizam magia (85%), summoners com o servitor assistindo e viajando junto do dono.
- **Painel GM ampliado**: lista paginada, chat público controlado pelo GM, toggles persistentes (chat dos bots, auto-start, PK, contar-online, limite de população editável).
- **PK com nível mínimo** + drops retail ao morrer + re-equipamento automático das peças perdidas.
- Refinos de realismo: teleport "tímido" (não teleportam na frente de jogadores reais), dispersão anti-aglomeração, aparência válida para HF, nomes variados, cadência de IA irregular.

## 4. Compatibilidade / notas

- Testado em **L2J Mobius CT 2.6 High Five**, JDK 25, in-game.
- Client-less: **não interfere com sistemas HWID / anti-multiconta** baseados em `EnterWorld` (os phantoms não passam por lá).
- Teste de carga informal: ~1000 phantoms ativos em VPS 12GB/4-cores → CPU 53–73%, 0 erros.
- Config em runtime em `config/Custom/PhantomAI.ini` (gerado automaticamente com defaults no primeiro arranque do sistema).

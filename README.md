# L2 Phantom AI — Port para High Five (Chronicle 2.6)

Port funcional do sistema **L2 Phantom AI** para **L2J Mobius CT 2.6 High Five**.

> **Sistema original e autoria:** **Cristian Barboza — MiaCodeWEB** ([miacodeweb.com](https://miacodeweb.com)).
> Este port é uma adaptação para High Five feita pela **Rekiem Games Network**, **com a permissão do autor**, sobre o sistema original de Mobius Essence RoseVain.
> O crédito ao MiaCodeWEB está visível in-game (banner de inicialização do GameServer e rodapé do painel `.pmenu`).
>
> Projetos originais do autor:
> - Mobius Essence (RoseVain): https://www.l2jbrasil.com/topic/150633-mod-l2-phantom-ai-manager-bots-inteligentes-geodata-fix-gemini-ai-l2j-mobius-essence-rosevain/
> - aCis 409: https://www.l2jbrasil.com/topic/150725-mod-l2-phantom-ai-manager-bots-inteligentes-gemini-ai-l2j-acis-l2jacis-409/
> - GitHub: https://github.com/miacodeweb/L2-Phantom-AI

---

## O que é

Jogadores **phantom** (bots) autônomos que fazem o servidor parecer vivo: nascem, evoluem em spots reais do datapack, se equipam por grade, farmam, usam skills, fazem PvP/PK entre si, conversam, morrem e revivem. São personagens reais do banco de dados — **client-less** (`Player.create()/load()` sem `GameClient`, conta `phantom_ai`) — e por isso **não interferem com sistemas HWID / anti-multiconta** baseados em `EnterWorld`.

Tudo é gerenciado in-game pelo painel de GM (`.pmenu`), sem precisar digitar comandos. O gerenciador de população inicia sozinho no boot do GameServer.

O que este port adicionou sobre o sistema original está detalhado em [`PORT_NOTES.md`](PORT_NOTES.md), que separa os **recursos originais do autor** dos **recursos adicionados no port**.

## Estrutura

```
data/scripts/custom/PhantomManager/   ← os 14 scripts do datapack (compilam no boot do GS)
config/Custom/PhantomPlayers.xml       ← persistência dos IDs de phantoms (vazio no início)
core-patches/                          ← 3 patches ao CORE do servidor (OBRIGATÓRIOS, ver abaixo)
PORT_NOTES.md                          ← mapeamento Essence→HF, detalhe das mudanças, o que é de quem
```

## Instalação

1. Copie `data/scripts/custom/PhantomManager/` para o seu `dist/game/data/scripts/custom/`.
2. Copie `config/Custom/PhantomPlayers.xml` para o seu `dist/game/config/Custom/`.
3. **Aplique os 3 patches de `core-patches/`** (imprescindíveis, ver seção abaixo).
4. Recompile o GameServer e reinicie.
5. Com um GM: `.pmenu` → criar/gerenciar phantoms.

## ⚠️ Os 3 patches ao core são OBRIGATÓRIOS

Um `Player` sem `GameClient` quebra suposições do core que assumem um `EnterWorld` ou a existência de cliente. Sem estes 3 patches, o sistema **compila mas se comporta mal** (dano que não aplica, efeitos visuais que não aparecem, mensagens que denunciam os bots):

| Patch | Arquivo | O que corrige |
|---|---|---|
| `01-Player-broadcastCharInfo` | `Player.java` | `broadcastCharInfo()` descartava **todas** as atualizações visuais em tempo real de players sem cliente (karma, buffs/debuffs, troca de equipamento). Guard `isOnlineInt()==0` → `!isOnline()`. |
| `02-Q00255_Tutorial-onKill-nullguard` | `Q00255_Tutorial.java` | NPE a cada mob de tutorial morto por um phantom (não têm quest state). Null-guard. |
| `03-ChatWhisper-offline-mode` | `ChatWhisper.java` | Sussurrar a um phantom retornava *"Player is in offline mode."* (denunciava). Limitado aos vendedores offline reais. |

Os três são defeitos genéricos de qualquer sistema de fake players client-less no Mobius, não específicos deste projeto — inclusive candidatos a contribuição upstream.

## Testado

- L2J Mobius CT 2.6 High Five, JDK 25, in-game.
- Teste de carga informal: ~1000 phantoms ativos em VPS 12GB/4-cores → CPU 53–73%, 0 erros.

## Créditos

Sistema original **L2 Phantom AI Manager** por **MiaCodeWEB — Cristian Barboza** ([miacodeweb.com](https://miacodeweb.com) · contacto@miacodeweb.com). Port para High Five por **Rekiem Games Network**, com permissão do autor.

## Screenshots

<img width="325" height="415" alt="image" src="https://github.com/user-attachments/assets/d2bd80be-45c8-4598-8ea3-11d51eba8e0d" />
<img width="334" height="419" alt="image" src="https://github.com/user-attachments/assets/caebe611-d456-4db6-86bc-c15b8657d3c6" />
<img width="329" height="423" alt="image" src="https://github.com/user-attachments/assets/65770ad0-7d9a-4d9a-b036-3e63fa0daea7" />

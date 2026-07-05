# L2 Phantom AI — Port a High Five (Chronicle 2.6)

Port funcional del sistema **L2 Phantom AI** de MiaCodeWEB a **L2J Mobius CT 2.6 High Five**.

> **Sistema original y autoría:** **Cristian Barboza — MiaCodeWEB** ([miacodeweb.com](https://miacodeweb.com)).
> Este port es una adaptación a High Five realizada por **Rekiem Games Network**, sobre su sistema original de Mobius Essence RoseVain.
> El crédito a MiaCodeWEB está visible in-game (banner de arranque del GameServer y pie del panel `.pmenu`).

Cristian: aquí tienes la versión HF terminada y funcionando. Está entera, probada in-game y con documentación de todo lo que hubo que tocar. Úsala como quieras — si te sirve publicarla, integrarla o lo que sea, es tuya.

---

## Qué es esto

Tu sistema Phantom AI portado y validado en High Five: los phantoms nacen, levean por spots reales del datapack, se equipan por grado, farmean, hacen PvP/PK entre ellos, chatean, mueren y reviven — todo client-less (`Player.create()/load()` sin `GameClient`, cuenta `phantom_ai`), gestionado desde `.pmenu`.

Sobre tu base añadimos bastante para dejarlo listo para producción; lo tienes detallado en [`PORT_NOTES.md`](PORT_NOTES.md) separando **lo que es tuyo original** de **lo que añadió Rekiem**, por si quieres quedarte solo con una parte.

## Estructura

```
data/scripts/custom/PhantomManager/   ← los 14 scripts del datapack (compilan en el boot del GS)
config/Custom/PhantomPlayers.xml       ← persistencia de IDs de phantoms (vacío al empezar)
core-patches/                          ← 3 parches al CORE del servidor (OBLIGATORIOS, ver abajo)
PORT_NOTES.md                          ← mapeo Essence→HF, detalle de cambios, qué es de quién
```

## Instalación rápida

1. Copia `data/scripts/custom/PhantomManager/` a tu `dist/game/data/scripts/custom/`.
2. Copia `config/Custom/PhantomPlayers.xml` a tu `dist/game/config/Custom/`.
3. **Aplica los 3 parches de `core-patches/`** (imprescindibles, ver siguiente sección).
4. Rebuild del GameServer + reinicia.
5. Con un GM: `.pmenu` → crear/gestionar phantoms. (El gestor de población arranca solo en el boot.)

## ⚠️ Los 3 parches al core son OBLIGATORIOS

Un `Player` sin `GameClient` rompe supuestos del core que asumen que hubo un `EnterWorld` o que hay cliente. Sin estos 3 parches el sistema **compila pero se comporta mal** (daño que no se aplica, efectos visuales que no se ven, mensajes que delatan a los bots):

| Parche | Fichero | Qué arregla |
|---|---|---|
| `01-Player-broadcastCharInfo` | `Player.java` | `broadcastCharInfo()` descartaba **todas** las actualizaciones visuales en vivo de players sin cliente (karma, buffs/debuffs, cambios de equipo). Guard `isOnlineInt()==0` → `!isOnline()`. |
| `02-Q00255_Tutorial-onKill-nullguard` | `Q00255_Tutorial.java` | NPE por cada mob de tutorial que mata un phantom (no tienen quest state). Null-guard. |
| `03-ChatWhisper-offline-mode` | `ChatWhisper.java` | Susurrar a un phantom devolvía *"Player is in offline mode."* (delator). Limitado a vendedores offline reales. |

Los tres son defectos genéricos de cualquier sistema de fake players client-less en Mobius, no específicos de este proyecto — de hecho son candidatos a contribución upstream.

## Contacto

Port por Rekiem Games Network. Sistema original de **MiaCodeWEB** — [miacodeweb.com](https://miacodeweb.com) · contacto@miacodeweb.com

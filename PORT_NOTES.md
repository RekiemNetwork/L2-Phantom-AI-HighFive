# Notas del port Essence → High Five

Documento técnico de la adaptación. Objetivo: que puedas ver exactamente **qué cambió respecto a tu versión Essence** y **qué añadió Rekiem** sobre tu base.

---

## 1. Mapeo de API Essence (RoseVain) → High Five (CT 2.6)

Los paths `org.l2jmobius.*` son idénticos entre crónicas (Mobius unificó el naming), así que el grueso compiló directo. Los cambios necesarios fueron de API/semántica:

| Essence | High Five | Nota |
|---|---|---|
| `setReputation(-X)` | `setKarma(+X)` | **Signo invertido**: en HF el karma es positivo para PK. |
| `broadcastReputation()` | `broadcastKarma()` | |
| `chargeShot(type)` | `setChargedShot(type, true)` | |
| `doAutoAttack(target)` | `getAI().setIntention(Intention.ATTACK, target)` | El `doAttack` de HF es **un solo golpe**, no auto-ataque sostenido. La intención ATTACK mantiene el ciclo y la aproximación. |
| `getInstanceWorld()` / `setInstance()` | `getInstanceId()` / `setInstanceId(int)` | HF usa ids `int`, no objeto `Instance`. |
| `PlayerClass.KAMAEL_SOLDIER` | `MALE_SOLDIER` / `FEMALE_SOLDIER` | En HF las clases base Kamael tienen género fijo (se fuerza el sexo del appearance según la clase). |
| `Race.SYLPH`, `Race.HIGH_ELF`, `SYLPH_GUNNER` | *(eliminados)* | No existen en HF; fuera del roster de creación. |

## 2. Trampas de players client-less resueltas (además del mapeo)

Cosas que "compilaban" pero fallaban en runtime, más allá del cambio de API:

- **Idioma nulo + multilang**: sin cliente nadie fija `_lang`; con multilang activo, `sendDamageMessage` reventaba con NPE en `NpcNameLocalisationData` y **abortaba la cadena de daño** (pegaban sin quitar vida ni generar aggro). Fix: `setLang(default)` al preparar el phantom.
- **IDs de items entre crónicas**: los IDs de equipo de Essence apuntaban a **items distintos** en HF (magos con dos cascos y sin ropa, armadura grado A a nivel 20, EtcItems inequipables como "armas"). Todos los packs se reconstruyeron con IDs **verificados uno a uno** contra `data/stats/items` de HF.
- **Formato de spawns HF**: los spawns territoriales (`<spawn zone=>`) no llevan coordenadas por-npc; se resuelven al centroide del polígono `<territory>`. Un parser de Essence cargaba 0 spots sin dar error.
- **Skills de summon**: no se detectan por nombre ("Summon Treasure Key"/"Siege Golem" también empiezan por "Summon"); se filtran por la clase del efecto `Summon` real.
- **Servitor + login**: la restauración del servitor vive en `EnterWorld` (cliente); sin ella la entrada de `character_summons` del logout anterior bloquea `canSummon` para siempre. Los phantoms la ejecutan al loguear.
- **`online` en BD**: `isOnlineInt()` devuelve 0 sin cliente y el autosave lo reescribe; para que la web cuente los bots, se fuerza `online=1` al loguear y se reafirma cada tick del gestor (toggle configurable).

Los tres parches de `core-patches/` son la parte de esto que no se puede resolver desde el datapack.

## 3. Qué es TUYO (original) vs qué añadió Rekiem

Para que puedas quedarte con lo que quieras:

**Tu sistema original (conservado, adaptado a HF):**
- Arquitectura modular (Phantom* separados), creación client-less, farmeo en spots reales del datapack, geodata-safe spawning, metas de nivel con descanso en ciudad, MP-rest de magos, packs de equipo por grado con variantes, shots automáticos, limpieza de inventario, PvP/PK, panel GM `.pmenu` + bypass, chat opcional (Gemini).

**Añadido por Rekiem sobre tu base:**
- **Gestor de población autónomo**: curva de 4 tramos horarios editables (% del pool, persistidos en `config/Custom/PhantomAI.ini`), sesiones de 2–5h con rotación gradual, auto-arranque en el boot (sin `.pstart` manual tras reinicios).
- **Pirámide de niveles** al crear (población repartida por el mapa) + rango de nivel manual.
- **Comportamiento humano**: pociones de tienda usadas en combate, huida a HP crítico, sentarse a regenerar con decisión pelear-o-huir al ser atacado, magos que priorizan magia (85%), summoners con su servitor asistiendo y viajando con el dueño.
- **Panel GM ampliado**: lista paginada, puppeteo de chat público, toggles persistentes (chat de bots, auto-arranque, PK, contar-online-web, tope de población editable).
- **PK gateado a nivel 20** con drops retail al morir + re-equipado automático de piezas perdidas.
- Refinos de realismo: teleport "tímido" (no se teletransportan delante de jugadores reales), dispersión anti-montón, apariencia válida H5, nombres multi-estilo, cadencia de IA irregular.

## 4. Compatibilidad / notas

- Probado en **L2J Mobius CT 2.6 High Five**, JDK 25, in-game en servidor de test.
- Client-less: **no interfiere con sistemas HWID / anti-multicuenta** basados en `EnterWorld` (los phantoms no pasan por ahí).
- Test de carga informal: ~1000 phantoms activos en un VPS 12GB/4-cores → CPU 53–73%, 0 errores.
- Config runtime en `config/Custom/PhantomAI.ini` (se autogenera con defaults en el primer arranque del sistema).

---

Cualquier duda sobre el port, encantados de ayudar. — Rekiem Games Network

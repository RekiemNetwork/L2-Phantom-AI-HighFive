package custom.PhantomManager;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Gestor automatico de poblacion de phantoms.<br>
 * Mantiene un numero de phantoms online que varia por hora del dia (curva de poblacion realista) y
 * rota individuos mediante sesiones de duracion limitada, de forma que ningun personaje aparezca online
 * 24/7. Arranca en el boot del GameServer, asi que tras un reinicio los phantoms vuelven solos sin
 * necesidad de {@code .pstart} manual.
 * @author Rekiem (port High Five de L2 Phantom AI - MiaCodeWEB)
 */
public class PhantomPopulation
{
	private static ScheduledFuture<?> _task = null;

	public static synchronized void start()
	{
		if (_task != null)
		{
			return;
		}
		// Primer arranque a los 60s (dejar asentar el server), luego revision periodica.
		_task = ThreadPool.scheduleAtFixedRate(PhantomPopulation::tick, 60000, PhantomConfig.POPULATION_TICK_MS);
		System.out.println(">>> [PHANTOM SYSTEM] Gestor de poblacion activo (curva horaria + sesiones).");
	}

	public static synchronized void stop()
	{
		if (_task != null)
		{
			_task.cancel(false);
			_task = null;
		}
	}

	public static synchronized boolean isActive()
	{
		return _task != null;
	}

	private static void tick()
	{
		try
		{
			final long now = System.currentTimeMillis();

			// El autoguardado del core reescribe characters.online con isOnlineInt()=0 (sin cliente): re-afirmar el flag para que la web los cuente.
			if (PhantomConfig.COUNT_ONLINE_WEB)
			{
				PhantomEngine.applyOnlineFlagToAll(true);
			}

			// 1. Expira sesiones terminadas (salida gradual: como maximo POPULATION_STEP por revision).
			int loggedOut = 0;
			for (Player phantom : PhantomEngine.activePhantoms)
			{
				if (loggedOut >= PhantomConfig.POPULATION_STEP)
				{
					break;
				}
				final long end = PhantomState.SESSION_END.getOrDefault(phantom.getObjectId(), 0L);
				if ((end > 0) && (now > end))
				{
					PhantomEngine.logoutPhantom(phantom);
					loggedOut++;
				}
			}

			// 2. Ajusta a la poblacion objetivo de la hora actual (entrada/salida gradual).
			final int target = PhantomConfig.targetForHour(LocalTime.now().getHour());
			final int current = PhantomEngine.activePhantoms.size();
			if (current < target)
			{
				final int brought = PhantomEngine.bringOnline(Math.min(PhantomConfig.POPULATION_STEP, target - current));
				if (brought > 0)
				{
					PhantomManager.logToFile("POPULATION", "Conectados " + brought + " (online " + PhantomEngine.activePhantoms.size() + "/" + target + ").");
				}
			}
			else if (current > (target + PhantomConfig.POPULATION_STEP))
			{
				trimExcess(Math.min(PhantomConfig.POPULATION_STEP, current - target), now);
				PhantomManager.logToFile("POPULATION", "Recorte a objetivo (online " + PhantomEngine.activePhantoms.size() + "/" + target + ").");
			}
		}
		catch (Exception e)
		{
			PhantomManager.logException("POPULATION", "Error en tick de poblacion", e);
		}
	}

	private static void trimExcess(int count, long now)
	{
		// Desconecta primero a los mas cercanos a terminar su sesion (salida natural, no aleatoria).
		final List<Player> candidates = new ArrayList<>(PhantomEngine.activePhantoms);
		candidates.sort(Comparator.comparingLong(p -> PhantomState.SESSION_END.getOrDefault(p.getObjectId(), Long.MAX_VALUE)));
		int trimmed = 0;
		for (Player phantom : candidates)
		{
			if (trimmed >= count)
			{
				break;
			}
			PhantomEngine.logoutPhantom(phantom);
			trimmed++;
		}
	}
}

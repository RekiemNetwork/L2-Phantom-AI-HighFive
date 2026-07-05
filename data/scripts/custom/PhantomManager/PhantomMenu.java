package custom.PhantomManager;

import java.time.LocalTime;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

public class PhantomMenu
{
	public static void showMenu(Player player)
	{
		if ((player == null) || !player.isGM())
		{
			return;
		}

		final int hour = LocalTime.now().getHour();
		final int online = PhantomEngine.activePhantoms.size();
		final int target = PhantomConfig.targetForHour(hour);
		final boolean auto = PhantomPopulation.isActive();

		StringBuilder html = new StringBuilder();
		html.append("<html><title>Phantom Manager</title><body>");
		html.append("<center><font color=\"LEVEL\">Panel de Control de Phantoms</font></center><br>");

		html.append("<center><table width=270>");
		html.append("<tr><td width=135>Online / pool:</td><td width=135><font color=\"00FF00\">").append(online).append("</font> / ").append(PhantomConfig.PHANTOM_IDS.size()).append("</td></tr>");
		html.append("<tr><td width=135>Objetivo (").append(hour).append("h):</td><td width=135>").append(target).append(" bots</td></tr>");
		html.append("<tr><td width=135>Tope online:</td><td width=135>").append(PhantomConfig.POPULATION_MAX).append("</td></tr>");
		html.append("<tr><td width=135>Gestor poblacion:</td><td width=135>").append(auto ? "<font color=\"00FF00\">ACTIVO</font>" : "<font color=\"FF5555\">PARADO</font>").append("</td></tr>");
		html.append("<tr><td width=135>Logs TXT:</td><td width=135>").append(PhantomManager.isDebugMode() ? "<font color=\"00FF00\">ON</font>" : "<font color=\"FF5555\">OFF</font>").append("</td></tr>");

		header(html, "Poblacion automatica");
		html.append("<tr><td width=135><center>").append(button(auto ? "Parar gestor" : "Activar gestor", auto ? "phantom_pop_off" : "phantom_pop_on", 125)).append("</center></td>");
		html.append("<td width=135><center>").append(button("Refrescar panel", "phantom_menu", 125)).append("</center></td></tr>");
		html.append("</table></center><br>");
		html.append("<center><font color=\"9F9F9F\">Densidad % actual: ").append(PhantomConfig.HOURLY_CURVE[0]).append("/").append(PhantomConfig.HOURLY_CURVE[1]).append("/").append(PhantomConfig.HOURLY_CURVE[2]).append("/").append(PhantomConfig.HOURLY_CURVE[3]).append("</font></center><br>");
		html.append("<center><table width=270>");
		html.append("<tr><td width=70>0-6h</td><td width=65><edit var=\"c1\" width=40 height=12></td>");
		html.append("<td width=70>6-12h</td><td width=65><edit var=\"c2\" width=40 height=12></td></tr>");
		html.append("<tr><td width=70>12-18h</td><td width=65><edit var=\"c3\" width=40 height=12></td>");
		html.append("<td width=70>18-24h</td><td width=65><edit var=\"c4\" width=40 height=12></td></tr>");
		html.append("</table></center><br>");
		html.append("<center>").append(button("Guardar tramos (rellena los 4)", "phantom_curve $c1 $c2 $c3 $c4", 200)).append("</center>");
		html.append("<center><table width=270>");
		html.append("<tr><td width=70>Tope:</td><td width=65><edit var=\"pmax\" width=40 height=12></td><td width=135><center>").append(button("Guardar tope", "phantom_maxpop $pmax", 125)).append("</center></td></tr>");
		html.append("</table></center>");
		html.append("<center><table width=270>");

		header(html, "Lotes manuales");
		html.append("<tr><td width=135><center>").append(button("Conectar 10", "phantom_start_10", 125)).append("</center></td>");
		html.append("<td width=135><center>").append(button("Desconectar 10", "phantom_stop_10", 125)).append("</center></td></tr>");
		html.append("<tr><td width=135><center>").append(button("Detener TODO", "phantom_stop_all", 125)).append("</center></td>");
		html.append("<td width=135><center>").append(button("Recargar XML", "phantom_reload_xml", 125)).append("</center></td></tr>");

		header(html, "Crear phantoms");
		html.append("<tr><td width=135><center>").append(button("Crear 10", "phantom_create_10", 125)).append("</center></td>");
		html.append("<td width=135><center>").append(button("Crear 25", "phantom_create_25", 125)).append("</center></td></tr>");
		html.append("<tr><td width=135><center>").append(button("Crear 50", "phantom_create_50", 125)).append("</center></td>");
		html.append("<td width=135><center>").append(button("Crear 100", "phantom_create 100", 125)).append("</center></td></tr>");
		html.append("</table></center>");

		html.append("<center><table width=270>");
		html.append("<tr><td width=55>Cant.</td><td width=42><edit var=\"qty\" width=38 height=12></td><td width=42></td><td width=131><center>").append(button("Crear (piramide)", "phantom_create $qty", 125)).append("</center></td></tr>");
		html.append("<tr><td width=55></td><td width=42><font color=\"9F9F9F\">min</font></td><td width=42><font color=\"9F9F9F\">max</font></td><td width=131></td></tr>");
		html.append("<tr><td width=55>Nivel:</td><td width=42><edit var=\"lvmin\" width=38 height=12></td><td width=42><edit var=\"lvmax\" width=38 height=12></td><td width=131><center>").append(button("Crear con nivel", "phantom_create $qty $lvmin $lvmax", 125)).append("</center></td></tr>");
		html.append("</table></center>");

		html.append("<center><table width=270>");
		header(html, "Chat de phantom");
		html.append("<tr><td width=60>Nombre:</td><td width=210><edit var=\"pmn\" width=195 height=12></td></tr>");
		html.append("<tr><td width=60>Texto:</td><td width=210><edit var=\"pmt\" width=195 height=12></td></tr>");
		html.append("<tr><td width=60></td><td width=210><center>").append(button("Decir en publico", "phantom_say $pmn $pmt", 125)).append("</center></td></tr>");
		html.append("</table></center>");

		html.append("<center><table width=270>");
		header(html, "Otros");
		html.append("<tr><td width=135><center>").append(button("Lista de phantoms", "phantom_list 1", 125)).append("</center></td>");
		html.append("<td width=135><center>").append(button("Logs ON/OFF", "phantom_debug", 125)).append("</center></td></tr>");
		html.append("<tr><td width=135><center>").append(button("Chat bots: " + (PhantomConfig.CHAT_ENABLED ? "ON" : "OFF"), "phantom_chat_toggle", 125)).append("</center></td>");
		html.append("<td width=135><center>").append(button("Auto-boot: " + (PhantomConfig.POPULATION_AUTO ? "ON" : "OFF"), "phantom_autostart_toggle", 125)).append("</center></td></tr>");
		html.append("<tr><td width=135><center>").append(button("PK bots: " + (PhantomConfig.PK_ENABLED ? "ON" : "OFF"), "phantom_pk_toggle", 125)).append("</center></td>");
		html.append("<td width=135><center>").append(button("Web online: " + (PhantomConfig.COUNT_ONLINE_WEB ? "ON" : "OFF"), "phantom_online_toggle", 125)).append("</center></td></tr>");
		html.append("</table></center><br>");

		html.append("<center><font color=\"777777\">L2 Phantom AI by MiaCodeWEB (miacodeweb.com)</font></center>");
		html.append("</body></html>");

		NpcHtmlMessage msg = new NpcHtmlMessage();
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}

	private static void header(StringBuilder html, String title)
	{
		html.append("<tr><td colspan=2><br><center><font color=\"B09878\">").append(title).append("</font></center></td></tr>");
	}

	static String button(String label, String bypass, int width)
	{
		return "<button value=\"" + label + "\" action=\"bypass -h " + bypass + "\" width=" + width + " height=21 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
	}
}

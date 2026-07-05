package custom.PhantomManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

public class PhantomHtml
{
	private static final int PAGE_SIZE = 10;

	public static void showList(Player player, int page)
	{
		if ((player == null) || !player.isGM())
		{
			return;
		}

		final List<Player> phantoms = new ArrayList<>(PhantomEngine.activePhantoms);
		phantoms.sort(Comparator.comparingInt(Player::getLevel).reversed());
		final int pages = Math.max(1, (phantoms.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		final int current = Math.max(1, Math.min(page, pages));

		StringBuilder html = new StringBuilder();
		html.append("<html><title>Phantoms ").append(current).append("/").append(pages).append("</title><body>");
		html.append("<center><font color=\"LEVEL\">Phantoms online: ").append(phantoms.size()).append("</font></center><br>");

		if (phantoms.isEmpty())
		{
			html.append("<center>No hay phantoms conectados.</center><br>");
		}
		else
		{
			html.append("<center><table width=270>");
			final int start = (current - 1) * PAGE_SIZE;
			final int end = Math.min(start + PAGE_SIZE, phantoms.size());
			for (int i = start; i < end; i++)
			{
				final Player p = phantoms.get(i);
				html.append("<tr><td width=98>").append(p.getName()).append(" <font color=\"B09878\">L").append(p.getLevel()).append("</font>").append(p.isDead() ? " <font color=\"FF5555\">+</font>" : "").append("</td>");
				html.append("<td width=43>").append(PhantomMenu.button("Ir", "phantom_go " + p.getName(), 41)).append("</td>");
				html.append("<td width=43>").append(PhantomMenu.button("Traer", "phantom_bring " + p.getName(), 41)).append("</td>");
				html.append("<td width=43>").append(PhantomMenu.button("Matar", "phantom_kill " + p.getName(), 41)).append("</td>");
				html.append("<td width=43>").append(PhantomMenu.button("Echar", "phantom_logout " + p.getName(), 41)).append("</td></tr>");
			}
			html.append("</table></center><br>");
		}

		html.append("<center><table width=280><tr>");
		if (current > 1)
		{
			html.append("<td>").append(PhantomMenu.button("< Anterior", "phantom_list " + (current - 1), 85)).append("</td>");
		}
		html.append("<td><center>Pag. ").append(current).append("/").append(pages).append("</center></td>");
		if (current < pages)
		{
			html.append("<td>").append(PhantomMenu.button("Siguiente >", "phantom_list " + (current + 1), 85)).append("</td>");
		}
		html.append("</tr></table></center><br>");
		html.append("<center>").append(PhantomMenu.button("Volver al panel", "phantom_menu", 140)).append("</center>");
		html.append("</body></html>");

		NpcHtmlMessage msg = new NpcHtmlMessage();
		msg.setHtml(html.toString());
		player.sendPacket(msg);
	}
}

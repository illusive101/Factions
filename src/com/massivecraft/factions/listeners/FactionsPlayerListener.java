package com.massivecraft.factions.listeners;

import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.Conf;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.P;
import com.massivecraft.factions.integration.SpoutFeatures;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.zcore.util.TextUtil;


public class FactionsPlayerListener implements Listener
{
	public P p;
	public FactionsPlayerListener(P p)
	{
		this.p = p;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		// Make sure that all online players do have a fplayer.
		final FPlayer me = FPlayers.i.get(event.getPlayer());
		
		// Update the lastLoginTime for this fplayer
		me.setLastLoginTime(System.currentTimeMillis());

/*		This is now done in a separate task which runs every few minutes
		// Run the member auto kick routine. Twice to get to the admins...
		FPlayers.i.autoLeaveOnInactivityRoutine();
		FPlayers.i.autoLeaveOnInactivityRoutine();
 */

		SpoutFeatures.updateAppearancesShortly(event.getPlayer());
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerQuit(PlayerQuitEvent event)
	{
		FPlayer me = FPlayers.i.get(event.getPlayer());

		// Make sure player's power is up to date when they log off.
		me.getPower();
		// and update their last login time to point to when the logged off, for auto-remove routine
		me.setLastLoginTime(System.currentTimeMillis());

		Faction myFaction = me.getFaction();
		if (myFaction != null)
		{
			myFaction.memberLoggedOff();
		}
		SpoutFeatures.playerDisconnect(me);
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		FPlayer me = FPlayers.i.get(player);
		
		// Did we change coord?
		FLocation from = me.getLastStoodAt();
		FLocation to = new FLocation(player.getLocation());
		
		if (from.equals(to))
		{
			return;
		}
		
		// Yes we did change coord (:
		
		me.setLastStoodAt(to);

		// Did we change "host"(faction)?
		boolean spoutClient = SpoutFeatures.availableFor(player);
		Faction factionFrom = Board.getFactionAt(from);
		Faction factionTo = Board.getFactionAt(to);
		boolean changedFaction = (factionFrom != factionTo);

		if (changedFaction && SpoutFeatures.updateTerritoryDisplay(me))
			changedFaction = false;

		if (me.isMapAutoUpdating())
		{
			me.sendMessage(Board.getMap(me.getFaction(), to, player.getLocation().getYaw()));

			if (spoutClient && Conf.spoutTerritoryOwnersShow)
				SpoutFeatures.updateOwnerList(me);
		}
		else
		{
			Faction myFaction = me.getFaction();
			String ownersTo = myFaction.getOwnerListString(to);

			if (changedFaction)
			{
				me.sendFactionHereMessage();
				if
				(
					Conf.ownedAreasEnabled
					&&
					Conf.ownedMessageOnBorder
					&&
					(
						!spoutClient
						||
						!Conf.spoutTerritoryOwnersShow
					)
					&&
					myFaction == factionTo
					&&
					!ownersTo.isEmpty()
				)
				{
					me.sendMessage(Conf.ownedLandMessage+ownersTo);
				}
			}
			else if (spoutClient && Conf.spoutTerritoryOwnersShow)
			{
				SpoutFeatures.updateOwnerList(me);
			}
			else if
			(
				Conf.ownedAreasEnabled
				&&
				Conf.ownedMessageInsideTerritory
				&&
				factionFrom == factionTo
				&&
				myFaction == factionTo
			)
			{
				String ownersFrom = myFaction.getOwnerListString(from);
				if (Conf.ownedMessageByChunk || !ownersFrom.equals(ownersTo))
				{
					if (!ownersTo.isEmpty())
						me.sendMessage(Conf.ownedLandMessage+ownersTo);
					else if (!Conf.publicLandMessage.isEmpty())
						me.sendMessage(Conf.publicLandMessage);
				}
			}
		}
		
		if (me.getAutoClaimFor() != null)
		{
			me.attemptClaim(me.getPlayer(), me.getAutoClaimFor(), player.getLocation(), true);
		}
		else if (me.isAutoSafeClaimEnabled())
		{
			if ( ! Permission.MANAGE_SAFE_ZONE.has(player))
			{
				me.setIsAutoSafeClaimEnabled(false);
			}
			else
			{
				FLocation playerFlocation = new FLocation(me);

				if (!Board.getFactionAt(playerFlocation).isSafeZone())
				{
					Board.setFactionAt(Factions.i.getSafeZone(), playerFlocation);
					me.msg("<i>This land is now a safe zone.");
				}
			}
		}
		else if (me.isAutoWarClaimEnabled())
		{
			if ( ! Permission.MANAGE_WAR_ZONE.has(player))
			{
				me.setIsAutoWarClaimEnabled(false);
			}
			else
			{
				FLocation playerFlocation = new FLocation(me);

				if (!Board.getFactionAt(playerFlocation).isWarZone())
				{
					Board.setFactionAt(Factions.i.getWarZone(), playerFlocation);
					me.msg("<i>This land is now a war zone.");
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerInteract(PlayerInteractEvent event)
	{
		Block block = event.getClickedBlock();
		Player player = event.getPlayer();
		FLocation mine = new FLocation(player.getLocation());
		Faction pLoc = Board.getFactionAt(mine);
		
		if (pLoc.isPeaceful() || pLoc.isSafeZone() || pLoc.isWarZone())
		{
	        if (event.getItem() != null 
	        		&& event.getItem().getType() == Material.ENDER_PEARL) 
	        {
	            event.getPlayer().sendMessage("Ender Pearl teleportation disabled in safe/war/peaceful zones!");
	            event.setCancelled(true);
	        }
		}

		
		if (event.isCancelled()) return;



		if (block == null)
		{
			return;  // clicked in air, apparently
		}

		if ( ! canPlayerUseBlock(player, block, false))
		{
			event.setCancelled(true);
			return;
		}

		if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
		{
			return;  // only interested on right-clicks for below
		}

		// workaround fix for new CraftBukkit 1.1-R1 bug where half-step on half-step placement doesn't trigger BlockPlaceEvent
		if (
				event.hasItem()
				&&
				event.getItem().getType() == Material.STEP
				&&
				block.getType() == Material.STEP
				&&
				event.getBlockFace() == BlockFace.UP
				&&
				event.getItem().getData().getData() == block.getData()
				&&
				! FactionsBlockListener.playerCanBuildDestroyBlock(player, block.getLocation(), "build", false)
			)
		{
			event.setCancelled(true);
			return;
		}

		if ( ! playerCanUseItemHere(player, block.getLocation(), event.getMaterial(), false))
		{
			event.setCancelled(true);
			return;
		}
	}

	public static boolean playerCanUseItemHere(Player player, Location location, Material material, boolean justCheck)
	{
		String name = player.getName();
		if (Conf.playersWhoBypassAllProtection.contains(name)) return true;

		FPlayer me = FPlayers.i.get(name);
		if (me.isAdminBypassing()) return true;

		FLocation loc = new FLocation(location);
		Faction otherFaction = Board.getFactionAt(loc);

		if (otherFaction.hasPlayersOnline())
		{
			if ( ! Conf.territoryDenyUseageMaterials.contains(material))
				return true; // Item isn't one we're preventing for online factions.
		}
		else
		{
			if ( ! Conf.territoryDenyUseageMaterialsWhenOffline.contains(material))
				return true; // Item isn't one we're preventing for offline factions.
		}

		if (otherFaction.isNone())
		{
			if (!Conf.wildernessDenyUseage || Conf.worldsNoWildernessProtection.contains(location.getWorld().getName()))
				return true; // This is not faction territory. Use whatever you like here.
			
			if (!justCheck)
				me.msg("<b>You can't use <h>%s<b> in the wilderness.", TextUtil.getMaterialName(material));

			return false;
		}
		else if (otherFaction.isSafeZone())
		{
			if (!Conf.safeZoneDenyUseage || Permission.MANAGE_SAFE_ZONE.has(player))
				return true;

			if (!justCheck)
				me.msg("<b>You can't use <h>%s<b> in a safe zone.", TextUtil.getMaterialName(material));

			return false;
		}
		else if (otherFaction.isWarZone())
		{
			if (!Conf.warZoneDenyUseage || Permission.MANAGE_WAR_ZONE.has(player))
				return true;

			if (!justCheck)
				me.msg("<b>You can't use <h>%s<b> in a war zone.", TextUtil.getMaterialName(material));

			return false;
		}

		Faction myFaction = me.getFaction();
		Relation rel = myFaction.getRelationTo(otherFaction);

		// Cancel if we are not in our own territory
		if (rel.confDenyUseage())
		{
			if (!justCheck)
				me.msg("<b>You can't use <h>%s<b> in the territory of <h>%s<b>.", TextUtil.getMaterialName(material), otherFaction.getTag(myFaction));

			return false;
		}

		// Also cancel if player doesn't have ownership rights for this claim
		if (Conf.ownedAreasEnabled && Conf.ownedAreaDenyUseage && !otherFaction.playerHasOwnershipRights(me, loc))
		{
			if (!justCheck)
				me.msg("<b>You can't use <h>%s<b> in this territory, it is owned by: %s<b>.", TextUtil.getMaterialName(material), otherFaction.getOwnerListString(loc));

			return false;
		}

		return true;
	}

	public static boolean canPlayerUseBlock(Player player, Block block, boolean justCheck)
	{
		String name = player.getName();
		if (Conf.playersWhoBypassAllProtection.contains(name)) return true;

		FPlayer me = FPlayers.i.get(name);
		if (me.isAdminBypassing()) return true;

		Material material = block.getType();
		FLocation loc = new FLocation(block);
		Faction otherFaction = Board.getFactionAt(loc);

		// no door/chest/whatever protection in wilderness, war zones, or safe zones
		if (!otherFaction.isNormal())
			return true;

		// We only care about some material types.
		if (otherFaction.hasPlayersOnline())
		{
			if ( ! Conf.territoryProtectedMaterials.contains(material))
				return true;
		}
		else
		{
			if ( ! Conf.territoryProtectedMaterialsWhenOffline.contains(material))
				return true;
		}

		Faction myFaction = me.getFaction();
		Relation rel = myFaction.getRelationTo(otherFaction);

		// You may use any block unless it is another faction's territory...
		if (rel.isNeutral() || (rel.isEnemy() && Conf.territoryEnemyProtectMaterials) || (rel.isAlly() && Conf.territoryAllyProtectMaterials))
		{
			if (!justCheck)
				me.msg("<b>You can't %s <h>%s<b> in the territory of <h>%s<b>.", (material == Material.SOIL ? "trample" : "use"), TextUtil.getMaterialName(material), otherFaction.getTag(myFaction));

			return false;
		}

		// Also cancel if player doesn't have ownership rights for this claim
		if (Conf.ownedAreasEnabled && Conf.ownedAreaProtectMaterials && !otherFaction.playerHasOwnershipRights(me, loc))
		{
			if (!justCheck)
				me.msg("<b>You can't use <h>%s<b> in this territory, it is owned by: %s<b>.", TextUtil.getMaterialName(material), otherFaction.getOwnerListString(loc));
			
			return false;
		}

		return true;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerRespawn(PlayerRespawnEvent event)
	{
		FPlayer me = FPlayers.i.get(event.getPlayer());

		me.getPower();  // update power, so they won't have gained any while dead

		Location home = me.getFaction().getHome();
		if
		(
			Conf.homesEnabled
			&&
			Conf.homesTeleportToOnDeath
			&&
			home != null
			&&
			(
				Conf.homesRespawnFromNoPowerLossWorlds
				||
				! Conf.worldsNoPowerLoss.contains(event.getPlayer().getWorld().getName())
			)
		)
		{
			event.setRespawnLocation(home);
		}
	}

	// For some reason onPlayerInteract() sometimes misses bucket events depending on distance (something like 2-3 blocks away isn't detected),
	// but these separate bucket events below always fire without fail
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event)
	{
		if (event.isCancelled()) return;

		Block block = event.getBlockClicked();
		Player player = event.getPlayer();

		if ( ! playerCanUseItemHere(player, block.getLocation(), event.getBucket(), false))
		{
			event.setCancelled(true);
			return;
		}
	}
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerBucketFill(PlayerBucketFillEvent event)
	{
		if (event.isCancelled()) return;

		Block block = event.getBlockClicked();
		Player player = event.getPlayer();

		if ( ! playerCanUseItemHere(player, block.getLocation(), event.getBucket(), false))
		{
			event.setCancelled(true);
			return;
		}
	}

	public static boolean preventCommand(String fullCmd, Player player)
	{
		if ((Conf.territoryNeutralDenyCommands.isEmpty() && Conf.territoryEnemyDenyCommands.isEmpty() && Conf.permanentFactionMemberDenyCommands.isEmpty()))
			return false;

		fullCmd = fullCmd.toLowerCase();

		FPlayer me = FPlayers.i.get(player);

		String shortCmd;  // command without the slash at the beginning
		if (fullCmd.startsWith("/"))
			shortCmd = fullCmd.substring(1);
		else
		{
			shortCmd = fullCmd;
			fullCmd = "/" + fullCmd;
		}

		if
		(
			me.hasFaction()
			&&
			! me.isAdminBypassing()
			&&
			! Conf.permanentFactionMemberDenyCommands.isEmpty()
			&&
			me.getFaction().isPermanent()
			&&
			isCommandInList(fullCmd, shortCmd, Conf.permanentFactionMemberDenyCommands.iterator())
		)
		{
			me.msg("<b>You can't use the command \""+fullCmd+"\" because you are in a permanent faction.");
			return true;
		}

		if (!me.isInOthersTerritory())
		{
			return false;
		}

		Relation rel = me.getRelationToLocation();
		if (rel.isAtLeast(Relation.ALLY))
		{
			return false;
		}

		if
		(
			rel.isNeutral()
			&&
			! Conf.territoryNeutralDenyCommands.isEmpty()
			&&
			! me.isAdminBypassing()
			&&
			isCommandInList(fullCmd, shortCmd, Conf.territoryNeutralDenyCommands.iterator())
		)
		{
			me.msg("<b>You can't use the command \""+fullCmd+"\" in neutral territory.");
			return true;
		}

		if
		(
			rel.isEnemy()
			&&
			! Conf.territoryEnemyDenyCommands.isEmpty()
			&&
			! me.isAdminBypassing()
			&&
			isCommandInList(fullCmd, shortCmd, Conf.territoryEnemyDenyCommands.iterator())
		)
		{
			me.msg("<b>You can't use the command \""+fullCmd+"\" in enemy territory.");
			return true;
		}

		return false;
	}

	private static boolean isCommandInList(String fullCmd, String shortCmd, Iterator<String> iter)
	{
		String cmdCheck;
		while (iter.hasNext())
		{
			cmdCheck = iter.next();
			if (cmdCheck == null)
			{
				iter.remove();
				continue;
			}

			cmdCheck = cmdCheck.toLowerCase();
			if (fullCmd.startsWith(cmdCheck) || shortCmd.startsWith(cmdCheck))
				return true;
		}
		return false;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerKick(PlayerKickEvent event)
	{
		if (event.isCancelled()) return;

		FPlayer badGuy = FPlayers.i.get(event.getPlayer());
		if (badGuy == null)
		{
			return;
		}

		SpoutFeatures.playerDisconnect(badGuy);

		// if player was banned (not just kicked), get rid of their stored info
		if (Conf.removePlayerDataWhenBanned && event.getReason().equals("Banned by admin."))
		{
			if (badGuy.getRole() == Role.ADMIN)
				badGuy.getFaction().promoteNewLeader();

			badGuy.leave(false);
			badGuy.detach();
		}
	}
}

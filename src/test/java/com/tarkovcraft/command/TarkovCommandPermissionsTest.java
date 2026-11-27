package com.tarkovcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarkovCommandPermissionsTest {
	@Test
	void adminPanelUsesTheSamePermissionBoundaryAsItsCommands() {
		var dispatcher = new CommandDispatcher<ServerCommandSource>();
		TarkovCommand.register(dispatcher);
		var root = dispatcher.findNode(List.of("tarkov"));
		assertNotNull(root.getChild("admin").getCommand());
		assertFalse(root.canUse(source(1)));
		assertTrue(root.canUse(source(2)));
		assertTrue(dispatcher.findNode(List.of("raid")).canUse(source(1)));
	}

	@Test
	void mapEditorRequiresPermissionLevelTwo() {
		var dispatcher = new CommandDispatcher<ServerCommandSource>();
		TarkovCommand.register(dispatcher);
		var lobby = dispatcher.findNode(List.of("tarkov", "lobby"));
		assertNotNull(lobby.getChild("toggle").getCommand());
		assertFalse(lobby.canUse(source(1)));
		assertTrue(lobby.canUse(source(2)));
	}

	private static ServerCommandSource source(int permissionLevel) {
		return new ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null, permissionLevel,
			"operator", Text.literal("Operator"), null, null);
	}
}

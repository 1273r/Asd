package dev.skybeat.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import dev.skybeat.Skybeat;

public class SkybeatGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Skybeat.LOGGER.info("[test] starting");
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			Skybeat.LOGGER.info("[test] world created");
			world.getClientWorld().waitForChunksRender();
			Skybeat.LOGGER.info("[test] chunks rendered");
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @a 0 90 0 0 -12");
			world.getClientWorld().waitForChunksRender();
			context.takeScreenshot("skybeat_0_before");
			Skybeat.LOGGER.info("[test] first screenshot");

			context.runOnClient(client -> Skybeat.addSource(new SyntheticSource()));
			context.waitTicks(70);
			context.takeScreenshot("skybeat_1_noon");
			context.waitTicks(7);
			context.takeScreenshot("skybeat_2_noon_later");

			world.getServer().runCommand("time set midnight");
			context.waitTicks(10);
			context.takeScreenshot("skybeat_3_night");

			world.getServer().runCommand("tp @a 0 90 0 90 -70");
			context.waitTicks(10);
			context.takeScreenshot("skybeat_4_up");
		}
	}
}

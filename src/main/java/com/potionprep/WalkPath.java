package com.potionprep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import net.runelite.api.CollisionDataFlag;

/**
 * Shortest walk across the loaded scene, from the game's collision flags:
 * fewest steps over 8 directions (every step is one tick, the same moves
 * the game allows), and of those the one with fewest turns. Coordinates
 * are scene tiles.
 */
final class WalkPath
{
	private WalkPath()
	{
	}

	/** Flags that block a step out of a tile in a direction, indexed by dx+1, dy+1. */
	private static final int[][] OUT = {
		{CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST, CollisionDataFlag.BLOCK_MOVEMENT_WEST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST},
		{CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, 0, CollisionDataFlag.BLOCK_MOVEMENT_NORTH},
		{CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST, CollisionDataFlag.BLOCK_MOVEMENT_EAST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST},
	};

	private static final int DIRS = 8;
	private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
	private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};
	/** A step costs far more than a turn, so turns only break ties. */
	private static final int STEP = 1000;

	private static boolean in(int[][] f, int x, int y)
	{
		return x >= 0 && y >= 0 && x < f.length && y < f[x].length;
	}

	/** A single step, as the game checks it: walls on both sides, and both corners for diagonals. */
	static boolean canStep(int[][] f, int x, int y, int dx, int dy)
	{
		int nx = x + dx;
		int ny = y + dy;
		if (!in(f, nx, ny) || (f[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
		{
			return false;
		}
		if ((f[x][y] & OUT[dx + 1][dy + 1]) != 0 || (f[nx][ny] & OUT[1 - dx][1 - dy]) != 0)
		{
			return false;
		}
		if (dx != 0 && dy != 0)
		{
			return canStep(f, x, y, dx, 0) && canStep(f, x, y, 0, dy)
				&& canStep(f, x + dx, y, 0, dy) && canStep(f, x, y + dy, dx, 0);
		}
		return true;
	}

	/**
	 * @param reach how close to the target counts as arrived (patches are objects you stand beside)
	 * @return tiles from start to arrival as {x, y}, or empty if there's no loaded route
	 */
	static List<int[]> find(int[][] f, int sx, int sy, int tx, int ty, int reach)
	{
		return find(f, sx, sy, tx - reach + 1, ty - reach + 1, tx + reach - 1, ty + reach - 1);
	}

	/**
	 * Walk to stand beside an object covering the scene tiles minX..maxX, minY..maxY
	 * (e.g. a herb patch's footprint).
	 */
	static List<int[]> find(int[][] f, int sx, int sy, int minX, int minY, int maxX, int maxY)
	{
		if (f == null || !in(f, sx, sy))
		{
			return Collections.emptyList();
		}
		int w = f.length;
		int h = f[0].length;
		// Dijkstra over (tile, last direction): fewest steps first, then fewest
		// turns, so of the many equally short routes the line takes the clean one.
		int states = w * h * DIRS;
		int[] cost = new int[states];
		int[] parent = new int[states];
		Arrays.fill(cost, Integer.MAX_VALUE);
		PriorityQueue<int[]> queue = new PriorityQueue<>(Comparator.comparingInt((int[] a) -> a[1]));
		int startTile = sx * h + sy;
		// The start has no direction yet: seed it under every one, turn-free.
		for (int d = 0; d < DIRS; d++)
		{
			cost[startTile * DIRS + d] = 0;
			parent[startTile * DIRS + d] = -1;
			queue.add(new int[]{startTile * DIRS + d, 0});
		}
		while (!queue.isEmpty())
		{
			int[] head = queue.poll();
			int state = head[0];
			if (head[1] != cost[state])
			{
				continue;
			}
			int tile = state / DIRS;
			int dir = state % DIRS;
			int x = tile / h;
			int y = tile % h;
			// Beside the area: within one tile of it (or on it, for an open spot).
			int ddx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
			int ddy = y < minY ? minY - y : y > maxY ? y - maxY : 0;
			if (Math.max(ddx, ddy) <= 1)
			{
				List<int[]> path = new ArrayList<>();
				for (int n = state; n != -1; n = parent[n])
				{
					int t = n / DIRS;
					path.add(new int[]{t / h, t % h});
				}
				Collections.reverse(path);
				return path;
			}
			for (int d = 0; d < DIRS; d++)
			{
				int dx = DX[d];
				int dy = DY[d];
				if (!canStep(f, x, y, dx, dy))
				{
					continue;
				}
				int next = ((x + dx) * h + (y + dy)) * DIRS + d;
				int c = head[1] + STEP + (tile == startTile || d == dir ? 0 : 1);
				if (c < cost[next])
				{
					cost[next] = c;
					parent[next] = state;
					queue.add(new int[]{next, c});
				}
			}
		}
		return Collections.emptyList();
	}
}

package com.potionprep;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.CollisionDataFlag;

/**
 * Shortest walk across the loaded scene, from the game's collision flags:
 * a breadth-first search over 8 directions (every step is one tick), the
 * same moves the game allows. Coordinates are scene tiles.
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
		int[] parent = new int[w * h];
		Arrays.fill(parent, -1);
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		int start = sx * h + sy;
		parent[start] = start;
		queue.add(start);
		while (!queue.isEmpty())
		{
			int node = queue.poll();
			int x = node / h;
			int y = node % h;
			// Beside the area: within one tile of it (or on it, for an open spot).
			int ddx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
			int ddy = y < minY ? minY - y : y > maxY ? y - maxY : 0;
			if (Math.max(ddx, ddy) <= 1)
			{
				List<int[]> path = new ArrayList<>();
				for (int n = node; ; n = parent[n])
				{
					path.add(new int[]{n / h, n % h});
					if (n == start)
					{
						break;
					}
				}
				Collections.reverse(path);
				return path;
			}
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if ((dx != 0 || dy != 0) && canStep(f, x, y, dx, dy))
					{
						int next = (x + dx) * h + (y + dy);
						if (parent[next] == -1)
						{
							parent[next] = node;
							queue.add(next);
						}
					}
				}
			}
		}
		return Collections.emptyList();
	}
}

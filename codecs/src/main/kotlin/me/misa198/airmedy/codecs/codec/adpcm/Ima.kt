// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/adpcm/ima.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.adpcm

internal val stepTable=intArrayOf(
	7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
	19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
	50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
	130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
	337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
	876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
	2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
	5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
	15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767,
)
internal val indexTable=intArrayOf(-1,-1,-1,-1,2,4,6,8,-1,-1,-1,-1,2,4,6,8)
internal class ImaState {
 var predictor=0;var index=0
 fun next(nib:Int):Int {val step=stepTable[index];var diff=step shr 3;if(nib and 4!=0)diff+=step;if(nib and 2!=0)diff+=step shr 1;if(nib and 1!=0)diff+=step shr 2;if(nib and 8!=0)diff=-diff
 predictor=(predictor+diff).coerceIn(-32768,32767);index=(index+indexTable[nib]).coerceIn(0,88);return predictor}
}

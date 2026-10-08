// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/musepack/synth.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.musepack

internal fun mul(a:Float,b:Float):Float = a*b
internal class Synthesis {
val v=FloatArray(3264)
companion object {
val diOptInt=arrayOf(
	intArrayOf(0, -29, 213, -459, 2037, -5153, 6574, -37489, 75038, 37489, 6574, 5153, 2037, 459, 213, 29),
	intArrayOf(-1, -31, 218, -519, 2000, -5517, 5959, -39336, 74992, 35640, 7134, 4788, 2063, 401, 208, 26),
	intArrayOf(-1, -35, 222, -581, 1952, -5879, 5288, -41176, 74856, 33791, 7640, 4425, 2080, 347, 202, 24),
	intArrayOf(-1, -38, 225, -645, 1893, -6237, 4561, -43006, 74630, 31947, 8092, 4063, 2087, 294, 196, 21),
	intArrayOf(-1, -41, 227, -711, 1822, -6589, 3776, -44821, 74313, 30112, 8492, 3705, 2085, 244, 190, 19),
	intArrayOf(-1, -45, 228, -779, 1739, -6935, 2935, -46617, 73908, 28289, 8840, 3351, 2075, 197, 183, 17),
	intArrayOf(-1, -49, 228, -848, 1644, -7271, 2037, -48390, 73415, 26482, 9139, 3004, 2057, 153, 176, 16),
	intArrayOf(-2, -53, 227, -919, 1535, -7597, 1082, -50137, 72835, 24694, 9389, 2663, 2032, 111, 169, 14),
	intArrayOf(-2, -58, 224, -991, 1414, -7910, 70, -51853, 72169, 22929, 9592, 2330, 2001, 72, 161, 13),
	intArrayOf(-2, -63, 221, -1064, 1280, -8209, -998, -53534, 71420, 21189, 9750, 2006, 1962, 36, 154, 11),
	intArrayOf(-2, -68, 215, -1137, 1131, -8491, -2122, -55178, 70590, 19478, 9863, 1692, 1919, 2, 147, 10),
	intArrayOf(-3, -73, 208, -1210, 970, -8755, -3300, -56778, 69679, 17799, 9935, 1388, 1870, -29, 139, 9),
	intArrayOf(-3, -79, 200, -1283, 794, -8998, -4533, -58333, 68692, 16155, 9966, 1095, 1817, -57, 132, 8),
	intArrayOf(-4, -85, 189, -1356, 605, -9219, -5818, -59838, 67629, 14548, 9959, 814, 1759, -83, 125, 7),
	intArrayOf(-4, -91, 177, -1428, 402, -9416, -7154, -61289, 66494, 12980, 9916, 545, 1698, -106, 117, 7),
	intArrayOf(-5, -97, 163, -1498, 185, -9585, -8540, -62684, 65290, 11455, 9838, 288, 1634, -127, 111, 6),
	intArrayOf(-5, -104, 146, -1567, -45, -9727, -9975, -64019, 64019, 9975, 9727, 45, 1567, -146, 104, 5),
	intArrayOf(-6, -111, 127, -1634, -288, -9838, -11455, -65290, 62684, 8540, 9585, -185, 1498, -163, 97, 5),
	intArrayOf(-7, -117, 106, -1698, -545, -9916, -12980, -66494, 61289, 7154, 9416, -402, 1428, -177, 91, 4),
	intArrayOf(-7, -125, 83, -1759, -814, -9959, -14548, -67629, 59838, 5818, 9219, -605, 1356, -189, 85, 4),
	intArrayOf(-8, -132, 57, -1817, -1095, -9966, -16155, -68692, 58333, 4533, 8998, -794, 1283, -200, 79, 3),
	intArrayOf(-9, -139, 29, -1870, -1388, -9935, -17799, -69679, 56778, 3300, 8755, -970, 1210, -208, 73, 3),
	intArrayOf(-10, -147, -2, -1919, -1692, -9863, -19478, -70590, 55178, 2122, 8491, -1131, 1137, -215, 68, 2),
	intArrayOf(-11, -154, -36, -1962, -2006, -9750, -21189, -71420, 53534, 998, 8209, -1280, 1064, -221, 63, 2),
	intArrayOf(-13, -161, -72, -2001, -2330, -9592, -22929, -72169, 51853, -70, 7910, -1414, 991, -224, 58, 2),
	intArrayOf(-14, -169, -111, -2032, -2663, -9389, -24694, -72835, 50137, -1082, 7597, -1535, 919, -227, 53, 2),
	intArrayOf(-16, -176, -153, -2057, -3004, -9139, -26482, -73415, 48390, -2037, 7271, -1644, 848, -228, 49, 1),
	intArrayOf(-17, -183, -197, -2075, -3351, -8840, -28289, -73908, 46617, -2935, 6935, -1739, 779, -228, 45, 1),
	intArrayOf(-19, -190, -244, -2085, -3705, -8492, -30112, -74313, 44821, -3776, 6589, -1822, 711, -227, 41, 1),
	intArrayOf(-21, -196, -294, -2087, -4063, -8092, -31947, -74630, 43006, -4561, 6237, -1893, 645, -225, 38, 1),
	intArrayOf(-24, -202, -347, -2080, -4425, -7640, -33791, -74856, 41176, -5288, 5879, -1952, 581, -222, 35, 1),
	intArrayOf(-26, -208, -401, -2063, -4788, -7134, -35640, -74992, 39336, -5959, 5517, -2000, 519, -218, 31, 1),)
val diOpt=Array(32){i->FloatArray(16){j->(diOptInt[i][j].toDouble()/65536).toFloat()}}
const val c0 = 0.5024192929f
const val c1 = 0.5224986076f
const val c2 = 0.5669440627f
const val c3 = 0.6468217969f
const val c4 = 0.7881546021f
const val c5 = 1.0606776476f
const val c6 = 1.7224471569f
const val c7 = 5.1011486053f
const val d0 = 0.5097956061f
const val d1 = 0.6013448834f
const val d2 = 0.8999761939f
const val d3 = 2.5629155636f
const val e0 = 0.5411961079f
const val e1 = 1.3065630198f
const val f0 = 0.7071067691f
const val g0 = 0.5006030202f
const val g1 = 0.5054709315f
const val g2 = 0.5154473186f
const val g3 = 0.5310425758f
const val g4 = 0.5531039238f
const val g5 = 0.5829349756f
const val g6 = 0.6225041151f
const val g7 = 0.6748083234f
const val g8 = 0.7445362806f
const val g9 = 0.8393496275f
const val g10 = 0.9725682139f
const val g11 = 1.1694399118f
const val g12 = 1.4841645956f
const val g13 = 2.0577809811f
const val g14 = 3.4076085091f
const val g15 = 10.1900081635f
}
fun computeNewV(s:FloatArray,at:Int) {
	var tmp = 0f

	var A00 = s[0] + s[31]
	var A01 = s[1] + s[30]
	var A02 = s[2] + s[29]
	var A03 = s[3] + s[28]
	var A04 = s[4] + s[27]
	var A05 = s[5] + s[26]
	var A06 = s[6] + s[25]
	var A07 = s[7] + s[24]
	var A08 = s[8] + s[23]
	var A09 = s[9] + s[22]
	var A10 = s[10] + s[21]
	var A11 = s[11] + s[20]
	var A12 = s[12] + s[19]
	var A13 = s[13] + s[18]
	var A14 = s[14] + s[17]
	var A15 = s[15] + s[16]

	var B00 = A00 + A15
	var B01 = A01 + A14
	var B02 = A02 + A13
	var B03 = A03 + A12
	var B04 = A04 + A11
	var B05 = A05 + A10
	var B06 = A06 + A09
	var B07 = A07 + A08
	var B08 = mul(A00-A15, c0)
	var B09 = mul(A01-A14, c1)
	var B10 = mul(A02-A13, c2)
	var B11 = mul(A03-A12, c3)
	var B12 = mul(A04-A11, c4)
	var B13 = mul(A05-A10, c5)
	var B14 = mul(A06-A09, c6)
	var B15 = mul(A07-A08, c7)

	A00 = B00 + B07
	A01 = B01 + B06
	A02 = B02 + B05
	A03 = B03 + B04
	A04 = mul(B00-B07, d0)
	A05 = mul(B01-B06, d1)
	A06 = mul(B02-B05, d2)
	A07 = mul(B03-B04, d3)
	A08 = B08 + B15
	A09 = B09 + B14
	A10 = B10 + B13
	A11 = B11 + B12
	A12 = mul(B08-B15, d0)
	A13 = mul(B09-B14, d1)
	A14 = mul(B10-B13, d2)
	A15 = mul(B11-B12, d3)

	B00 = A00 + A03
	B01 = A01 + A02
	B02 = mul(A00-A03, e0)
	B03 = mul(A01-A02, e1)
	B04 = A04 + A07
	B05 = A05 + A06
	B06 = mul(A04-A07, e0)
	B07 = mul(A05-A06, e1)
	B08 = A08 + A11
	B09 = A09 + A10
	B10 = mul(A08-A11, e0)
	B11 = mul(A09-A10, e1)
	B12 = A12 + A15
	B13 = A13 + A14
	B14 = mul(A12-A15, e0)
	B15 = mul(A13-A14, e1)

	A00 = B00 + B01
	A01 = mul(B00-B01, f0)
	A02 = B02 + B03
	A03 = mul(B02-B03, f0)
	A04 = B04 + B05
	A05 = mul(B04-B05, f0)
	A06 = B06 + B07
	A07 = mul(B06-B07, f0)
	A08 = B08 + B09
	A09 = mul(B08-B09, f0)
	A10 = B10 + B11
	A11 = mul(B10-B11, f0)
	A12 = B12 + B13
	A13 = mul(B12-B13, f0)
	A14 = B14 + B15
	A15 = mul(B14-B15, f0)

	v[at+48] = -A00
	v[at+0] = A01
	v[at+8] = A03
	v[at+40] = -A02 - v[at+8]
	v[at+12] = A07
	v[at+4] = A05 + v[at+12]
	v[at+36] = -(v[at+4] + A06)
	v[at+44] = -A04 - A06 - A07
	v[at+14] = A15
	v[at+10] = A11 + v[at+14]
	v[at+6] = v[at+10] + A13
	v[at+2] = A09 + A13 + A15
	v[at+34] = -v[at+2] - A14
	v[at+38] = v[at+34] + A09 - A10 - A11
	tmp = -(A12 + A14 + A15)
	v[at+46] = tmp - A08
	v[at+42] = tmp - A10 - A11

	A00 = mul(s[0]-s[31], g0)
	A01 = mul(s[1]-s[30], g1)
	A02 = mul(s[2]-s[29], g2)
	A03 = mul(s[3]-s[28], g3)
	A04 = mul(s[4]-s[27], g4)
	A05 = mul(s[5]-s[26], g5)
	A06 = mul(s[6]-s[25], g6)
	A07 = mul(s[7]-s[24], g7)
	A08 = mul(s[8]-s[23], g8)
	A09 = mul(s[9]-s[22], g9)
	A10 = mul(s[10]-s[21], g10)
	A11 = mul(s[11]-s[20], g11)
	A12 = mul(s[12]-s[19], g12)
	A13 = mul(s[13]-s[18], g13)
	A14 = mul(s[14]-s[17], g14)
	A15 = mul(s[15]-s[16], g15)

	B00 = A00 + A15
	B01 = A01 + A14
	B02 = A02 + A13
	B03 = A03 + A12
	B04 = A04 + A11
	B05 = A05 + A10
	B06 = A06 + A09
	B07 = A07 + A08
	B08 = mul(A00-A15, c0)
	B09 = mul(A01-A14, c1)
	B10 = mul(A02-A13, c2)
	B11 = mul(A03-A12, c3)
	B12 = mul(A04-A11, c4)
	B13 = mul(A05-A10, c5)
	B14 = mul(A06-A09, c6)
	B15 = mul(A07-A08, c7)

	A00 = B00 + B07
	A01 = B01 + B06
	A02 = B02 + B05
	A03 = B03 + B04
	A04 = mul(B00-B07, d0)
	A05 = mul(B01-B06, d1)
	A06 = mul(B02-B05, d2)
	A07 = mul(B03-B04, d3)
	A08 = B08 + B15
	A09 = B09 + B14
	A10 = B10 + B13
	A11 = B11 + B12
	A12 = mul(B08-B15, d0)
	A13 = mul(B09-B14, d1)
	A14 = mul(B10-B13, d2)
	A15 = mul(B11-B12, d3)

	B00 = A00 + A03
	B01 = A01 + A02
	B02 = mul(A00-A03, e0)
	B03 = mul(A01-A02, e1)
	B04 = A04 + A07
	B05 = A05 + A06
	B06 = mul(A04-A07, e0)
	B07 = mul(A05-A06, e1)
	B08 = A08 + A11
	B09 = A09 + A10
	B10 = mul(A08-A11, e0)
	B11 = mul(A09-A10, e1)
	B12 = A12 + A15
	B13 = A13 + A14
	B14 = mul(A12-A15, e0)
	B15 = mul(A13-A14, e1)

	A00 = B00 + B01
	A01 = mul(B00-B01, f0)
	A02 = B02 + B03
	A03 = mul(B02-B03, f0)
	A04 = B04 + B05
	A05 = mul(B04-B05, f0)
	A06 = B06 + B07
	A07 = mul(B06-B07, f0)
	A08 = B08 + B09
	A09 = mul(B08-B09, f0)
	A10 = B10 + B11
	A11 = mul(B10-B11, f0)
	A12 = B12 + B13
	A13 = mul(B12-B13, f0)
	A14 = B14 + B15
	A15 = mul(B14-B15, f0)

	v[at+15] = A15
	v[at+13] = A07 + v[at+15]
	v[at+11] = v[at+13] + A11
	v[at+5] = v[at+11] + A05 + A13
	v[at+9] = A03 + A11 + A15
	v[at+7] = v[at+9] + A13
	v[at+1] = A01 + A09 + A13 + A15
	v[at+33] = -v[at+1] - A14
	v[at+3] = A05 + A07 + A09 + A13 + A15
	v[at+35] = -v[at+3] - A06 - A14
	tmp = -(A10 + A11 + A13 + A14 + A15)
	v[at+37] = tmp - A05 - A06 - A07
	v[at+39] = tmp - A02 - A03
	tmp += A13 - A12
	v[at+41] = tmp - A02 - A03
	v[at+43] = tmp - A04 - A06 - A07
	tmp = -(A08 + A12 + A14 + A15)
	v[at+47] = tmp - A00
	v[at+45] = tmp - A04 - A06 - A07

	v[at+32] = -v[at+0]
	v[at+31] = -v[at+1]
	v[at+30] = -v[at+2]
	v[at+29] = -v[at+3]
	v[at+28] = -v[at+4]
	v[at+27] = -v[at+5]
	v[at+26] = -v[at+6]
	v[at+25] = -v[at+7]
	v[at+24] = -v[at+8]
	v[at+23] = -v[at+9]
	v[at+22] = -v[at+10]
	v[at+21] = -v[at+11]
	v[at+20] = -v[at+12]
	v[at+19] = -v[at+13]
	v[at+18] = -v[at+14]
	v[at+17] = -v[at+15]

	v[at+63] = v[at+33]
	v[at+62] = v[at+34]
	v[at+61] = v[at+35]
	v[at+60] = v[at+36]
	v[at+59] = v[at+37]
	v[at+58] = v[at+38]
	v[at+57] = v[at+39]
	v[at+56] = v[at+40]
	v[at+55] = v[at+41]
	v[at+54] = v[at+42]
	v[at+53] = v[at+43]
	v[at+52] = v[at+44]
	v[at+51] = v[at+45]
	v[at+50] = v[at+46]
	v[at+49] = v[at+47]
}
fun frame(y:Array<FloatArray>,out:FloatArray,channel:Int,channels:Int) {
 v.copyInto(v,2304,0,960);var at=2304
 for(n in 0 until 36) {at-=64;computeNewV(y[n],at)
  for(k in 0 until 32) {val z=at+k;val d=diOpt[k]
   out[(n*32+k)*channels+channel] = mul(v[z+0],d[0]) + mul(v[z+96],d[1]) + mul(v[z+128],d[2]) + mul(v[z+224],d[3]) + mul(v[z+256],d[4]) + mul(v[z+352],d[5]) + mul(v[z+384],d[6]) + mul(v[z+480],d[7]) + mul(v[z+512],d[8]) + mul(v[z+608],d[9]) + mul(v[z+640],d[10]) + mul(v[z+736],d[11]) + mul(v[z+768],d[12]) + mul(v[z+864],d[13]) + mul(v[z+896],d[14]) + mul(v[z+992],d[15])
}}
}
fun reset(){v.fill(0f)}
}

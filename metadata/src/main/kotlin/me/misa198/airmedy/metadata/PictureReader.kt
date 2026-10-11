// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9, internal/id3/picture.go and bits/image.go.
// Copyright (c) 2026 Cole Springer, MIT; see metadata/THIRD-PARTY-NOTICES.
package me.misa198.airmedy.metadata

internal object PictureReader {
    fun read(input: Input, offset: Long, size: Int, v22: Boolean): Picture? {
        if (size < 1) return null
        val enc = input.bytes(offset,1).u(0)
        if (enc > 3) return null
        var pos = 1
        val mime: String
        if (v22) {
            if (size < 5) return null
            mime = picMime(input.bytes(offset+pos,3).latin())
            pos += 3
        } else {
            val bytes = mutableListOf<Byte>()
            var terminated = false
            while (pos < size && pos < 65536) {
                val value = input.bytes(offset+pos++,1)[0]
                if (value == 0.toByte()) { terminated = true; break }
                bytes += value
            }
            if (!terminated && pos < size) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"APIC MIME exceeds 64 KiB header limit")
            if (!terminated || pos >= size) return null
            mime = bytes.toByteArray().latin().trim()
        }
        if (pos >= size) return null
        val type = input.bytes(offset+pos++,1).u(0)
        val desc = mutableListOf<Byte>()
        val term = if(enc in 1..2) 2 else 1
        var terminated = false
        while (size-pos >= term && desc.size < 65536) {
            val next = input.bytes(offset+pos,term)
            pos += term
            if (next.all { it == 0.toByte() }) { terminated = true; break }
            desc.addAll(next.toList())
        }
        if (!terminated && size-pos >= term) throw ReadFailure(ErrorClass.SIZE_TOO_LARGE,"APIC description exceeds 64 KiB header limit")
        if (!terminated) return null
        val descBytes = desc.toByteArray()
        val description = Encoded(enc,descBytes).decode(0,descBytes.size)
        val payload = offset+pos
        val count = size-pos
        val image = if (mime == "-->") ImageInfo("-->") else sniff(input,payload,count)
        val hash = input.hash(payload,count)
        val loader: (() -> ByteArray)? = if (input.options.pictures == Pictures.FULL)
            { { Input(input.source,input.options).bytes(payload,count) } } else null
        return Picture(type,image.mime,description,image.width,image.height,image.depth,image.colors,count,hash,loader)
    }
    private fun sniff(input: Input, offset: Long, size: Int): ImageInfo {
        val h = input.bytes(offset,minOf(32,size))
        if (h.size >= 29 && h.copyOfRange(0,8).contentEquals(byteArrayOf(0x89.toByte(),80,78,71,13,10,26,10)) && h.latin(12,4) == "IHDR") {
            val channels = when(h.u(25)) { 0,3 -> 1; 2 -> 3; 4 -> 2; 6 -> 4; else -> 0 }
            var colors = 0
            if (h.u(25) == 3) {
                var p = 33L
                while (p+8 <= size) {
                    val chunk = input.bytes(offset+p,8)
                    val n = chunk.be(0,4)
                    if (n > size-p-12) break
                    if (chunk.latin(4,4) == "PLTE") { colors = (n/3).toInt(); break }
                    p += n+12
                }
            }
            return ImageInfo("image/png",h.be(16,4).toInt(),h.be(20,4).toInt(),h.u(24)*channels,colors)
        }
        if (h.size >= 3 && h.u(0) == 255 && h.u(1) == 216 && h.u(2) == 255) {
            var p = 2L
            while (p+4 <= size) {
                val marker = input.bytes(offset+p,2)
                if (marker.u(0) != 255) { p++; continue }
                val code = marker.u(1)
                if (code == 255) { p++; continue }
                if (code in 208..217 || code == 1 || code == 0) { p += 2; continue }
                val n = input.bytes(offset+p+2,2).be(0,2)
                if (n < 2) break
                if (code in listOf(192,193,194,195,197,198,199,201,202,203,205,206,207)) {
                    if (size-p < 10) break
                    val sof = input.bytes(offset+p+4,6)
                    return ImageInfo("image/jpeg",sof.be(3,2).toInt(),sof.be(1,2).toInt(),sof.u(0)*sof.u(5))
                }
                if (n > size-p-2) break
                p += 2+n
            }
            return ImageInfo("application/octet-stream")
        }
        if (h.size >= 13 && h.latin(0,6) in listOf("GIF87a","GIF89a")) {
            val depth = if(h.u(10) and 128 != 0) (h.u(10) and 7)+1 else 0
            val colors = if(h.u(10) and 128 != 0) 1 shl depth else 0
            return ImageInfo("image/gif",h.le(6,2).toInt(),h.le(8,2).toInt(),depth,colors)
        }
        if (h.size >= 12 && h.latin(0,4) == "RIFF" && h.latin(8,4) == "WEBP") {
            when(if(h.size >= 16) h.latin(12,4) else "") {
                "VP8X" -> if(h.size >= 30) return ImageInfo("image/webp",h.le(24,3).toInt()+1,h.le(27,3).toInt()+1)
                "VP8 " -> if(h.size >= 30 && h.u(23) == 0x9d && h.u(24) == 1 && h.u(25) == 42) return ImageInfo("image/webp",h.le(26,2).toInt() and 0x3fff,h.le(28,2).toInt() and 0x3fff)
                "VP8L" -> if(h.size >= 25 && h.u(20) == 47) { val v = h.le(21,4); return ImageInfo("image/webp",((v and 0x3fff)+1).toInt(),(((v shr 14) and 0x3fff)+1).toInt()) }
            }
            return ImageInfo("image/webp")
        }
        if (h.size >= 2 && h.latin(0,2) == "BM") {
            if(h.size < 18) return ImageInfo("image/bmp")
            val dib = h.le(14,4)
            if (dib >= 16 && h.size >= 30) return ImageInfo("image/bmp",kotlin.math.abs(h.le(18,4).toInt()),kotlin.math.abs(h.le(22,4).toInt()),h.le(28,2).toInt())
            if (dib == 12L && h.size >= 26) return ImageInfo("image/bmp",h.le(18,2).toInt(),h.le(20,2).toInt(),h.le(24,2).toInt())
            return ImageInfo("image/bmp")
        }
        if (h.size >= 4 && (h.latin(0,4) == "II*\u0000" || h.latin(0,4) == "MM\u0000*")) {
            val little = h[0] == 'I'.code.toByte()
            fun integer(data: ByteArray,p: Int,n: Int) = if(little) data.le(p,n) else data.be(p,n)
            var width = 0
            var height = 0
            if (h.size >= 8) {
                val ifd = integer(h,4,4)
                if (ifd >= 8 && ifd <= size-2) {
                    val count = integer(input.bytes(offset+ifd,2),0,2).toInt()
                    for(i in 0 until count) {
                        val p = ifd+2+12L*i
                        if(p+12 > size) break
                        val entry = input.bytes(offset+p,12)
                        if(integer(entry,4,4) != 1L) continue
                        val type = integer(entry,2,2).toInt()
                        val value = when(type) { 3 -> integer(entry,8,2).toInt(); 4 -> integer(entry,8,4).toInt(); else -> 0 }
                        when(integer(entry,0,2).toInt()) { 256 -> width=value; 257 -> height=value }
                    }
                }
            }
            return ImageInfo("image/tiff",width,height)
        }
        if (h.size >= 12 && h.latin(4,4) == "ftyp") {
            val brands = mapOf("heic" to "image/heic","heix" to "image/heic","mif1" to "image/heif","heim" to "image/heif",
                "heis" to "image/heif","avif" to "image/avif","hevc" to "image/heic-sequence","hevx" to "image/heic-sequence",
                "msf1" to "image/heif-sequence","hevm" to "image/heif-sequence","hevs" to "image/heif-sequence","avis" to "image/avif-sequence")
            val declared = h.be(0,4)
            val end = if(declared in 16..size.toLong()) declared else 12L
            var pos = 8L
            while (pos+4 <= end) {
                if(pos != 12L) brands[input.bytes(offset+pos,4).latin()]?.let { return ImageInfo(it) }
                pos += 4
            }
        }
        if(h.size >= 12 && h.copyOfRange(0,12).contentEquals(byteArrayOf(0,0,0,12,74,88,76,32,13,10,0x87.toByte(),10))) return ImageInfo("image/jxl")
        if(h.size >= 2 && h.u(0) == 255 && h.u(1) == 10) {
            var bit = 16
            var valid = true
            fun take(n: Int): Long {
                var out = 0L
                repeat(n) { i -> if(bit/8 >= h.size) valid=false else out = out or (((h.u(bit/8) shr (bit%8)) and 1).toLong() shl i); bit++ }
                return out
            }
            fun u32() = take(intArrayOf(9,13,18,30)[take(2).toInt()])+1
            val small = take(1) == 1L
            val height = if(small) (take(5)+1)*8 else u32()
            val ratio = take(3).toInt()
            val ratios = arrayOf(1 to 1,1 to 1,12 to 10,4 to 3,3 to 2,16 to 9,5 to 4,2 to 1)
            val width = if(ratio == 0) if(small) (take(5)+1)*8 else u32() else height*ratios[ratio].first/ratios[ratio].second
            if(valid) return if(width <= Int.MAX_VALUE && height <= Int.MAX_VALUE) ImageInfo("image/jxl",width.toInt(),height.toInt()) else ImageInfo("image/jxl")
        }
        return ImageInfo("application/octet-stream")
    }
}
private data class ImageInfo(val mime: String, val width: Int = 0, val height: Int = 0, val depth: Int = 0, val colors: Int = 0)

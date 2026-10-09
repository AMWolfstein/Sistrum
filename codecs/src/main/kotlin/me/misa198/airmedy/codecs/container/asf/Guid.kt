// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow container/asf/guid.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.container.asf

internal const val guidHeader="3026b2758e66cf11a6d900aa0062ce6c"
internal const val guidData="3626b2758e66cf11a6d900aa0062ce6c"
internal const val guidSimpleIndex="90080033b1e5cf1189f400a0c90349cb"
internal const val guidFileProperties="a1dcab8c47a9cf118ee400c00c205365"
internal const val guidStreamProperties="9107dcb7b7a9cf118ee600c00c205365"
internal const val guidHeaderExtension="b503bf5f2ea9cf118ee300c00c205365"
internal const val guidContentDescription="3326b2758e66cf11a6d900aa0062ce6c"
internal const val guidExtendedContentDescription="40a4d0d207e3d21197f000a0c95ea850"
internal const val guidMarker="01cd87f451a9cf118ee600c00c205365"
internal const val guidContentEncryption="fbb3112223bdd211b4b700a0c955fc6e"
internal const val guidExtendedContentEncryption="14e68a292226174cb935dae07ee9289c"
internal const val guidAdvancedContentEncryption="338505438169e6499b74ad12cb86d58c"
internal const val guidNoErrorCorrection="0057fb20555bcf11a8fd00805f5c442b"
internal const val guidAudioSpread="50cdc3bf8f61cf118bb200aa00b4e220"
internal const val guidAudioMedia="409e69f84d5bcf11a8fd00805f5c442b"
internal const val guidVideoMedia="c0ef19bc4d5bcf11a8fd00805f5c442b"
internal const val guidCommandMedia="c0cfda59e659d011a3ac00a0c90348f6"
internal const val guidBinaryMedia="e265fb3aef47f240ac2c70a90d71d343"
internal fun guidAt(b:ByteArray,at:Int):String {val out=StringBuilder(32);for(i in 0 until 16){val v=b[at+i].toInt() and 255;out.append("0123456789abcdef"[v ushr 4]);out.append("0123456789abcdef"[v and 15])};return out.toString()}
internal fun guidLabel(g:String):String {fun rev(s:String)=s.chunked(2).reversed().joinToString("")
return (rev(g.substring(0,8))+"-"+rev(g.substring(8,12))+"-"+rev(g.substring(12,16))+"-"+g.substring(16,20)+"-"+g.substring(20)).uppercase()}

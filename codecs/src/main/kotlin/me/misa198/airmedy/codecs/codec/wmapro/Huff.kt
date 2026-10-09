// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow codec/wmapro/huff.go, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
package me.misa198.airmedy.codecs.codec.wmapro

internal class Vlc(lens:IntArray,syms:IntArray) {
    private val kid:IntArray;private var maxLen=0
    init {
        require(lens.size==syms.size);val nodes=ArrayList<Int>();nodes.add(0);nodes.add(0);var acc=0
        for(i in lens.indices){val n=lens[i];if(n==0)continue;val code=acc ushr (32-n);acc+=1 shl (32-n);maxLen=maxOf(maxLen,n);var at=0
            for(b in n-1 downTo 0){val d=(code ushr b) and 1;val index=2*at+d
                if(b==0){check(nodes[index]==0);nodes[index]= -1-syms[i];break}
                var next=nodes[index];check(next>=0)
                if(next==0){nodes.add(0);nodes.add(0);next=nodes.size/2-1;nodes[index]=next};at=next
            }
        }
        kid=nodes.toIntArray()
    }
    fun decode(r:BitReader):Int {
        val acc=r.peek(maxLen);var at=0
        for(i in 0 until maxLen){val d=((acc ushr (maxLen-1-i)) and 1).toInt();val next=kid[2*at+d]
            if(next<0){if(r.pos+i+1>r.n){r.pos=r.n;r.overrun();return -1};r.pos+=i+1;return -1-next}
            if(next==0)return -1;at=next
        }
        return -1
    }
}
internal object Books {
    val scaleDelta=Vlc(scaleDeltaLens,scaleDeltaSyms)
    val scaleRunLevel=Vlc(scaleRunLevelLens,scaleRunLevelSyms)
    val coef=Array(2){Vlc(coefLens[it],coefSyms[it])}
    val vec4=Vlc(vec4Lens,vec4Syms);val vec2=Vlc(vec2Lens,vec2Syms);val vec1=Vlc(vec1Lens,vec1Syms)
}

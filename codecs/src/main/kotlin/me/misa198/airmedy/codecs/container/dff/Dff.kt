// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from dff-meta 0.2.0 src/{lib.rs,model.rs}, github.com/clone206/dff.
// Copyright 2020 Daniel J. R. May; modified by clone206.
// Original MIT OR Apache-2.0 notices retained in THIRD-PARTY-NOTICES.
package me.misa198.airmedy.codecs.container.dff

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.dsd.*

object Dff {
    fun open(source:RandomAccessSource,targetRate:Int=176400)=Dsd.open(source,targetRate)
    fun open(source:ByteBuffer,targetRate:Int=176400)=Dsd.open(source,targetRate)
    internal fun parse(source:RandomAccessSource):Header {
        fun check(ok:Boolean,error:String){if(!ok)throw IOException("dff: $error")}
        fun read(p:Long,n:Int)=headerBytes(source,p,n).order(ByteOrder.BIG_ENDIAN)
        val form=read(0,16);check(tag(form,0)=="FRM8","FORM chunk must start with 'FRM8'.");check(tag(form,12)=="DSD ","FORM chunk form type must be 'DSD '.")
        val version=read(16,16);check(tag(version,0)=="FVER","Format Version chunk must start with 'FVER'.");check(version.getLong(4)==4L,"FVER chunk data size must be 4.")
        var position=32L;var rate=0;var channels=0;var layout="";var foundProp=false
        while(position<=source.length-12){
            val h=read(position,12);val id=tag(h,0);val n=h.getLong(4);val at=position+12
            if(n<0||n>source.length-at)throw IOException("dsd: invalid data bounds")
            if(id=="DSD "){
                check(foundProp,"Chunk DSD  was found before it should have been.");check(n!=0L,"A DSD chunk must not have size 0.")
                check(channels!=0,"CHNL number not found or is unsupported.");return Header(rate,channels,layout,n/channels,at,false,false)
            }
            if(id=="PROP"&&!foundProp){
                check(n>=4,"CHNL chunk size does not match channel data.");check(tag(read(at,4),0)=="SND ","Property chunk type must be 'SND '.")
                var p=at+4
                while(p<at+n){val sh=read(p,12);val sid=tag(sh,0);val sn=sh.getLong(4);val data=p+12
                    if(sn<0||sn>at+n-data)throw IOException("dsd: invalid property bounds")
                    when(sid){
                        "FS  "->{check(sn==4L,"FS chunk size must be 4.");rate=read(data,4).getInt(0)}
                        "CHNL"->{check(sn>=2,"CHNL chunk size does not match channel data.");channels=read(data,2).getShort(0).toInt() and 65535;check(channels==1||channels==2,"CHNL number not found or is unsupported.");check(sn==2L+4*channels,"CHNL chunk size does not match channel data.");val ids=read(data+2,4*channels);layout=(0 until channels).joinToString(","){tag(ids,it*4).trim()}}
                        "CMPR"->{check(sn>=4,"CMPR chunk size invalid or inconsistent.");val type=tag(read(data,4),0);val name=if(sn-4<=256)String(read(data+4,(sn-4).toInt()).array(),Charsets.UTF_8)else "";check(type=="DSD "&&name!="DST Encoded","Compression type must be 'DSD '. DST not supported.")}
                        "ABSS"->check(sn==8L,"ABSS chunk size invalid.")
                        "LSCO"->check(sn==2L,"LSCO chunk size invalid or inconsistent.")
                    }
                    p=data+sn+(sn and 1L)
                };foundProp=true
            }
            position=at+n+(n and 1L)
        }
        throw IOException("dff: Unexpected end of file.")
    }
}

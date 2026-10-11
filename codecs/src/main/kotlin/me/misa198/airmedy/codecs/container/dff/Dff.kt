// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from dff-meta 0.2.0 src/{lib.rs,model.rs}, github.com/clone206/dff.
// Copyright 2020 Daniel J. R. May; modified by clone206.
// Original MIT OR Apache-2.0 notices retained in THIRD-PARTY-NOTICES.
// Multichannel CHNL validation and canonical ordering extend the pinned parser;
// see SISTRUM-PATCHES.md. Only uncompressed DSD is supported.
package me.misa198.airmedy.codecs.container.dff

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.misa198.airmedy.codecs.container.*
import me.misa198.airmedy.codecs.container.dsd.*

object Dff {
    fun open(source:RandomAccessSource,targetRate:Int=0)=Dsd.open(source,targetRate)
    fun open(source:ByteBuffer,targetRate:Int=0)=Dsd.open(source,targetRate)
    internal fun parse(source:RandomAccessSource):Header {
        fun check(ok:Boolean,error:String){if(!ok)throw IOException("dff: $error")}
        fun read(p:Long,n:Int)=headerBytes(source,p,n).order(ByteOrder.BIG_ENDIAN)
        val form=read(0,16)
        check(tag(form,0)=="FRM8","FORM chunk must start with 'FRM8'.")
        check(tag(form,12)=="DSD ","FORM chunk form type must be 'DSD '.")
        val version=read(16,16)
        check(tag(version,0)=="FVER","Format Version chunk must start with 'FVER'.")
        check(version.getLong(4)==4L,"FVER chunk data size must be 4.")
        var position=32L;var rate=0;var channels=0;var layout=""
        var order:IntArray?=null;var foundProp=false;var foundChannels=false;var foundCompression=false
        while(position<=source.length-12){
            val h=read(position,12);val id=tag(h,0);val n=h.getLong(4);val at=position+12
            if(n<0||n>source.length-at)throw IOException("dsd: invalid data bounds")
            if(id=="DSD "){
                check(foundProp,"Chunk DSD  was found before it should have been.")
                check(n!=0L,"A DSD chunk must not have size 0.")
                check(foundChannels,"Missing CHNL chunk.")
                check(foundCompression,"Missing CMPR chunk; uncompressed DSD must be declared.")
                check(n%channels==0L,"DSD data size does not match CHNL channel count.")
                return Header(rate,channels,layout,n/channels,at,false,false,order)
            }
            if(id=="PROP"&&!foundProp){
                check(n>=4,"Property chunk is too short.")
                check(tag(read(at,4),0)=="SND ","Property chunk type must be 'SND '.")
                var p=at+4
                while(p<at+n){
                    check(at+n-p>=12,"Truncated property chunk header.")
                    val sh=read(p,12);val sid=tag(sh,0);val sn=sh.getLong(4);val data=p+12
                    if(sn<0||sn>at+n-data)throw IOException("dsd: invalid property bounds")
                    check(sn+(sn and 1L)<=at+n-data,"Missing property chunk padding.")
                    when(sid){
                        "FS  "->{check(sn==4L,"FS chunk size must be 4.");rate=read(data,4).getInt(0)}
                        "CHNL"->{
                            check(!foundChannels,"Duplicate CHNL chunk.")
                            check(sn>=2,"CHNL chunk size does not match channel data.")
                            channels=read(data,2).getShort(0).toInt() and 65535
                            check(channels in 1..6,"CHNL channel count must be between 1 and 6.")
                            check(sn==2L+4*channels,"CHNL chunk size does not match channel data.")
                            val ids=read(data+2,4*channels)
                            val labels=Array(channels){ ch ->
                                val raw=tag(ids,ch*4)
                                when(raw){
                                    "SLFT","MLFT"->"FL";"SRGT","MRGT"->"FR"
                                    "LS  "->"BL";"RS  "->"BR";"C   "->"C";"LFE "->"LFE"
                                    else->{
                                        check(raw[0]=='c' && raw.substring(1).all{it in '0'..'9'},"Invalid CHNL channel ID '$raw'.")
                                        raw
                                    }
                                }
                            }
                            check(labels.toSet().size==channels,"Duplicate CHNL channel identity.")
                            val speakers=listOf("FL","FR","C","LFE","BL","BR")
                            // Stable sorting retains the input order of unspecified c000-c999 IDs.
                            order=(0 until channels).sortedBy{speakers.indexOf(labels[it]).let{rank->if(rank<0)6 else rank}}.toIntArray()
                            layout=order.joinToString(","){labels[it]}
                            foundChannels=true
                        }
                        "CMPR"->{
                            check(!foundCompression,"Duplicate CMPR chunk.")
                            check(sn>=4,"CMPR chunk size invalid or inconsistent.")
                            val type=tag(read(data,4),0)
                            check(type=="DSD ","Compression type must be 'DSD '. DST not supported.")
                            foundCompression=true
                        }
                        "ABSS"->check(sn==8L,"ABSS chunk size invalid.")
                        "LSCO"->check(sn==2L,"LSCO chunk size invalid or inconsistent.")
                    }
                    p=data+sn+(sn and 1L)
                }
                foundProp=true
            }
            check(id!="DST ","Compression type must be 'DSD '. DST not supported.")
            position=at+n+(n and 1L)
        }
        throw IOException("dff: Unexpected end of file.")
    }
}

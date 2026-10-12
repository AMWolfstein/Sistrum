package main
import("encoding/json";"os";"github.com/colespringer/waxflow/audio";"github.com/colespringer/waxflow/dsp/mix")
type Vector struct { Source,Target uint32; Refused bool; MaxGain float64; Input,Output [][]float32 }
func main(){vectors:=[]Vector{};sources:=[]audio.ChannelMask{3,4,8,51,55,63,1551,1599,319,262143,1<<20,0};for bit:=0;bit<18;bit++{sources=append(sources,1<<bit)}
 for _,src:=range sources {for _,dst:=range []audio.ChannelMask{3,4,51,55,63,319,1599,0} {
 m,err:=mix.For(src,dst);if err!=nil{vectors=append(vectors,Vector{Source:uint32(src),Target:uint32(dst),Refused:true});continue}
 in:=make([][]float32,m.In());out:=make([][]float32,m.Out());for c:=range in{in[c]=make([]float32,128);in[c][c]=1;for i:=m.In();i<128;i++{in[c][i]=float32(((i*31+c*17)%257)-128)/256}}
 for c:=range out{out[c]=make([]float32,128)};m.Apply(out,in,128)
 vectors=append(vectors,Vector{uint32(src),uint32(dst),false,m.MaxGain(),in,out})
 }};json.NewEncoder(os.Stdout).Encode(vectors)}

// Generated signal oracle; imports the pinned fork, never reproduces its DSP.
package main
import("encoding/json";"os";"github.com/colespringer/waxflow/dsp/resample")
type Vector struct { Profile string; InRate,OutRate,Channels,Offset int; Input,Output [][]float32 }
func main(){ vectors:=[]Vector{}; for _,p:=range []resample.Profile{resample.HQ,resample.Fast} { for _,rates:=range [][2]int{{44100,48000},{48000,44100},{48000,16000},{8000,48000}} { for _,ch:=range []int{1,2,3} { for _,off:=range []int{0,1} {
 r,err:=resample.New(rates[0],rates[1],ch,p);if err!=nil{panic(err)}; _,phase:=r.OffsetFor(int64(off));r.Reset(phase)
 src:=make([][]float32,ch); dst:=make([][]float32,ch); result:=make([][]float32,ch)
 for c:=range src { src[c]=make([]float32,513);dst[c]=make([]float32,37);for i:=range src[c] { src[c][i]=float32(((i*31+c*17)%257)-128)/256 };src[c][0]=1 }
 remain:=make([][]float32,ch);for c:=range src{remain[c]=src[c]}
 for len(remain[0])>0 {n,used:=r.Process(dst,remain);for c:=range src{result[c]=append(result[c],dst[c][:n]...);remain[c]=remain[c][used:]}}
 for {n:=r.Drain(dst);if n==0{break};for c:=range src{result[c]=append(result[c],dst[c][:n]...)}}
 vectors=append(vectors,Vector{string(p),rates[0],rates[1],ch,off,src,result})
 }}}}; if err:=json.NewEncoder(os.Stdout).Encode(vectors);err!=nil{panic(err)} }

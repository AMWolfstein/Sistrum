package main
import("encoding/json";"os";"math";"github.com/colespringer/waxflow/dsp/gain")
type Vector struct { Kind string; Rate,Channels int; Ceiling,DB,Linear float64; Input,Output [][]float32 }
func main(){vectors:=[]Vector{};for _,kind:=range []string{"compressor","limiter"} {for _,rate:=range []int{8000,44100,48000,96000} {for _,ch:=range []int{1,2,6} {for _,signal:=range []int{0,1,2} {
 src:=make([][]float32,ch);dst:=make([][]float32,ch);out:=make([][]float32,ch)
 for c:=range src{src[c]=make([]float32,2049);dst[c]=make([]float32,73);for i:=range src[c]{v:=float32(0);switch signal{case 0:v=float32(((i*31+c*17)%257)-128)/64;case 1:if i==0||i==256||i==1800{v=1.75};case 2:v=float32(.7*math.Sin(2*math.Pi*float64(i)*(float64(c)+.3)))};src[c][i]=v}}
 var process func([][]float32,[][]float32)(int,int);var drain func([][]float32)int
 if kind=="compressor"{k,e:=gain.NewCompressor(rate,ch,gain.PresetVoice);if e!=nil{panic(e)};process=k.Process;drain=k.Drain}else{k,e:=gain.NewLimiter(rate,ch,-1);if e!=nil{panic(e)};process=k.Process;drain=k.Drain}
 remain:=make([][]float32,ch);for c:=range src{remain[c]=src[c]}
 for len(remain[0])>0{n,used:=process(dst,remain);for c:=range src{out[c]=append(out[c],dst[c][:n]...);remain[c]=remain[c][used:]}}
 for{n:=drain(dst);if n==0{break};for c:=range src{out[c]=append(out[c],dst[c][:n]...)}}
 vectors=append(vectors,Vector{Kind:kind,Rate:rate,Channels:ch,Ceiling:-1,Input:src,Output:out})
 }}}};for _,db:=range []float64{-80,-20,-6,0,6,20}{src:=[][]float32{{-1,-.5,0,.5,1}};out:=[][]float32{append([]float32{},src[0]...)};linear:=gain.FromDB(db);gain.Apply(out[0],float32(linear));vectors=append(vectors,Vector{Kind:"scalar",DB:db,Linear:linear,Input:src,Output:out})};json.NewEncoder(os.Stdout).Encode(vectors)}

package main
import("encoding/json";"os";"math";"time";"github.com/colespringer/waxflow/dsp/silence")
type Vector struct { Rate,Channels int; DB float64; Nanos int64; Input [][]float32; Spans []silence.Span; Dropped int; DroppedSamples,TotalSamples,Samples int64 }
func main(){vectors:=[]Vector{};for _,rate:=range []int{8000,44100,48000} {for _,db:=range []float64{-80,-55,-40} {for _,ch:=range []int{1,2,6} {for _,ns:=range []int64{1,333000,700000} {
 d,err:=silence.New(rate,ch,db,time.Duration(ns));if err!=nil{panic(err)}
 src:=make([][]float32,ch);t:=float32(math.Pow(10,db/20));for c:=range src {src[c]=make([]float32,257);for i:=range src[c] {v:=float32(0);if i>=11&&i<45{v=t};if i>=90&&i<110 {v=math.Nextafter32(t,0)};if i>=170&&i<200 {v=math.Nextafter32(t,float32(math.Inf(1)))};if i>=240{v=-t};src[c][i]=v };if c==ch-1 {src[c][128]=1} }
 if err:=d.Process(src);err!=nil{panic(err)};d.Flush();vectors=append(vectors,Vector{rate,ch,db,ns,src,d.Spans(),d.Dropped(),d.DroppedSamples(),d.TotalSamples(),d.Samples()})
 }}}};json.NewEncoder(os.Stdout).Encode(vectors)}

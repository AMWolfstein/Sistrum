package main
import("encoding/json";"os";"github.com/colespringer/waxflow/dsp/fft")
type Vector struct { N int; Real,Imag,OutReal,OutImag []float32 }
func main(){vectors:=[]Vector{};for _,n:=range []int{1,2,4,16,64,256,1024,4096} {for signal:=0;signal<3;signal++ {
 re:=make([]float32,n);im:=make([]float32,n);ore:=make([]float32,n);oim:=make([]float32,n)
 for i:=range re {if signal==0 {if i==0{re[i]=1}} else {re[i]=float32((i*31%257)-128)/256;if signal==2{im[i]=float32((i*17%127)-63)/128}}}
 fft.NewPlan(n).Transform(ore,oim,re,im);vectors=append(vectors,Vector{n,re,im,ore,oim})
 }};json.NewEncoder(os.Stdout).Encode(vectors)}

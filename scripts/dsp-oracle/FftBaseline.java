import java.security.MessageDigest;
import java.lang.reflect.Method;
/** Records the pre-move JVM FFT, independent of the Go tolerance checks. */
class FftBaseline {
 public static void main(String[] args) throws Exception {
  Class<?> cls=Class.forName(args[0]);Method transform=cls.getMethod("transform",float[].class,float[].class,float[].class,float[].class);
  System.out.println("# Frozen before FFT relocation; original class from commit d170388.");
  System.out.println("# length\tsignal\tsha256_of_interleaved_little_endian_float32");
  for(int n:new int[]{1,2,4,16,64,256,1024,4096,8192})for(int signal=0;signal<3;signal++) {
   float[] re=new float[n],im=new float[n],outRe=new float[n],outIm=new float[n];
   for(int i=0;i<n;i++){if(signal==0){re[i]=i==0?1:0;}else {re[i]=((i*31%257)-128)/256f;if(signal==2)im[i]=((i*17%127)-63)/128f;}}
   transform.invoke(cls.getConstructor(int.class).newInstance(n),outRe,outIm,re,im);
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   for(int i=0;i<n;i++)for(float v:new float[]{outRe[i],outIm[i]}){int bits=Float.floatToRawIntBits(v);for(int b=0;b<4;b++)digest.update((byte)(bits >>> (b*8)));}
   System.out.println(n+"\t"+signal+"\t"+java.util.HexFormat.of().formatHex(digest.digest()));
  }
 }
}

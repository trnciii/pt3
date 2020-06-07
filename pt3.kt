import java.io.File
import kotlin.math.*
import kotlin.random.Random
import kotlin.system.*

fun kernel(x:Float, y:Float, scene:Scene):V{

	var ray = scene.camera.ray(x,y)
	var th = V(1f)
	var terminate = false

	while(!terminate){
		val pTerminate = maxOf(th.x, th.y, th.z)
		if(Random.nextFloat() < pTerminate) th /= V(pTerminate)
		else terminate = true

		val hit = scene.intersect(ray)

		when(hit.mtl.type){
			MtlType.EMISSIVE->{
				return th*hit.mtl.col
			}

			MtlType.LAMBERT->{
				val sg = if(hit.n.z<0) -1f else 1f
				val a = -1f/(sg+hit.n.z)
				val b = hit.n.x * hit.n.y * a;
				val tan = V(1f + sg*hit.n.x*hit.n.x*a, sg*b, -sg*hit.n.x)
				val btn = V(b, sg + hit.n.y*hit.n.y*a, -hit.n.y)

				val u1 = Random.nextFloat()
				val u2 = Random.nextFloat()*2f*PI.toFloat()
				val r = sqrt(u1)

				th *= hit.mtl.col
				ray = Ray(
					hit.pos + V(1e-5f)*hit.n,
					hit.n*V(sqrt(1f-u1)) + tan*V(r*cos(u2)) + btn*V(r*sin(u2))
				)
			}

			MtlType.GGXReflect->{
				val sg = if(hit.n.z<0) -1f else 1f
				val a = -1f/(sg+hit.n.z)
				val b = hit.n.x * hit.n.y * a;
				val tan = V(1f + sg*hit.n.x*hit.n.x*a, sg*b, -sg*hit.n.x)
				val btn = V(b, sg + hit.n.y*hit.n.y*a, -hit.n.y)

				val u1 = Random.nextFloat()
				val u2 = Random.nextFloat()*2f*PI.toFloat()

				val r2 = hit.mtl.a2*u1/(1f+u1*(hit.mtl.a2-1f))
				val r = sqrt(r2)

				th *= hit.mtl.col
				ray = Ray(
					hit.pos + V(1e-5f)*hit.n,
					hit.n*V(sqrt(1-r2)) + tan*V(r*cos(u2)) +btn*V(r*sin(u2))
				)
			}
		}
	}
	return V(0f)
}

fun main(args: Array<String>){
	
	val w = 512
	val h = 512
	val spp = 100

	var image = Array(w*h){V(0f)}
	val scene = Scene()

	val timeRender = measureTimeMillis {
		for(i in image.indices){
			val x:Int = i%w
			val y:Int = i/w

			for(n in 1..spp){
				val xf:Float = x.toFloat()+Random.nextFloat()
				val yf:Float = y.toFloat()+Random.nextFloat()
				image[i] += kernel(2f*xf/h.toFloat()-1f, -2f*yf/h.toFloat()+1f, scene)
			}

			image[i] *= V(1f/spp.toFloat())
		}
	}
	println("render finished in " + timeRender)

	var result = "P3\n" + w.toString() + " " + h.toString() + "\n" + "255\n"
	for(p in image){
		val r = tonemap(p.x).toString()
		val g = tonemap(p.y).toString()
		val b = tonemap(p.z).toString()

		result += r + " " +  g + " " + b + "\n"
	}
	
	val name = "result.ppm"
	File(name).writeText(result)
	println("image saved as " + name)
}

class Scene{
	var camera:Camera
	var shapes:Array<Object>
	val background:Material

	init{
		background = Material(MtlType.EMISSIVE, V(0.1f))
		camera = Camera()
		shapes = arrayOf(
			Sphere(V( 0f,-1f, 3f), 0.5f, Material(MtlType.EMISSIVE, V(6f))),
			Sphere(V(-1f, 0f, 0f), 1f, Material(MtlType.LAMBERT, V(0.9f, 0.1f, 0.1f))),
			Sphere(V( 1f, 1f, 0f), 1f, Material(MtlType.GGXReflect, V(0.1f, 0.1f, 0.9f), 0.05f)),
			Plane(V(0f, 0f, -1f), V(4f,0f,0f), V(0f,4f,0f), Material(MtlType.GGXReflect, V(0.1f, 0.8f, 0.8f), 0.1f))
		)
	}

	fun intersect(ray:Ray):Hit{
		var hit = Hit(V(0f), ray.d, 1e10f, background)
		for(shape in shapes){
			hit = shape.intersect(hit, ray)
		}

		if(dot(hit.n, ray.d)>0){
			hit.n = -hit.n
		}

		return hit
	}
}

enum class MtlType{
	EMISSIVE,
	LAMBERT,
	GGXReflect
}
class Material(	val type:MtlType,
				val col:V,
				val a2:Float){
	constructor(t:MtlType, c:V):this(t,c,0f){}
}

class Hit(	var pos:V,
			var n:V,
			var dist:Float,
			var mtl:Material)

interface Object{
	fun intersect(hit:Hit, ray:Ray):Hit
}

class Sphere(val center:V, val r:Float, val mtl:Material) :Object{
	fun dist(ray:Ray):Float{
		val CO = ray.o - center
		val b = dot(ray.d, CO)
		val c = dot(CO, CO)-r*r

		if(b*b<c)return -1f

		var t = b+sqrt(b*b-c)
		if(0<t) t = b-sqrt(b*b-c)
		return if(t<0) -t else -1f
	}

	override fun intersect(hit:Hit, ray:Ray):Hit{
		val t = dist(ray)
		if(hit.dist<t || t<0)return hit

		var pos = ray.o + V(t)*ray.d
		var nHit = Hit(pos, (pos-center)/V(r), t, mtl)
		return nHit
	}
}

class Plane(val center:V, val b1:V, val b2:V, val mtl:Material):Object{
	val n:V
	init{n=normalize(cross(b1,b2))}

	override fun intersect(hit:Hit, ray:Ray):Hit{
		val o = ray.o - center
		val q = cross(b1,o)
		val p = cross(b2,ray.d)
		val det = dot(b1,p)

		val u = dot(o,p)/det
		val v = dot(ray.d, q)/det
		if(abs(u)<1 && abs(v)<1){
			val t = dot(q,b2)/det
			if(0<t && t<hit.dist)return Hit(ray.o+V(t)*ray.d, n, t, mtl)
		}
		return hit
	}
}


class Camera{
	var pos:V
	var cx:V
	var cy:V
	var cz:V
	var fl:Float

	init{
		pos = V(0f,-5f,1f)
		cx = V(1f, 0f, 0f)
		cy = V(0f, 0f, 1f)
		cz = V(0f,-1f, 0f)
		fl = 2f
	}

	fun ray(x:Float, y:Float):Ray{
		val dir = cx*V(x) + cy*V(y) - cz*V(fl)
		return Ray(pos, normalize(dir))
	}
}

class Ray(var o:V, var d:V)

class V(var x:Float, var y:Float, var z:Float){
	constructor(v:Float):this(v,v,v){}
	constructor():this(0f,0f,0f){}

	operator fun plus(b:V)=V(x+b.x, y+b.y, z+b.z)
	operator fun minus(b:V)=V(x-b.x, y-b.y, z-b.z)
	operator fun unaryMinus()=V(-x, -y, -z)
	operator fun times(b:V)=V(x*b.x, y*b.y, z*b.z)
	operator fun div(b:V)=V(x/b.x, y/b.y, z/b.z)

	fun print(){
		print(this.x)
		print(", ")
		print(this.y)
		print(", ")
		print(this.z)
		print("\n")
	}
}

fun dot(a:V, b:V):Float{return a.x*b.x + a.y*b.y + a.z*b.z}
fun cross(a:V, b:V):V{return V(a.y*b.z - a.z*b.y, a.z*b.x - a.x*b.z, a.x*b.y - a.y*b.x)}
fun abs(a:V):Float{return sqrt(dot(a,a))}
fun normalize(a:V):V{val il=1f/abs(a); return V(a.x*il, a.y*il, a.z*il);}

fun tonemap(v:Float):Int{
	if(v>=1f)return 255
	if(v<=0f)return 0
	return (256f * v.pow(1f/2.2f)).toInt()
}
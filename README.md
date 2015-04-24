# haxl poc

Una prueba de concepto para implementar [haxl](http://community.haskell.org/~simonmar/papers/haxl-icfp14.pdf) en Scala, tal vez ingenuamente. Usa la librería estándar de Futuros para la ejecución y scalaz para ayuda sintáctica de aplicativos.

Por ahora el uso es medianamente torpe debido a la jerarquía de tipos. Tal vez esto se pueda solucionar definiendo una Typeclass. 

Un ejemplo de su potencial uso, tomado del artículo, se encuentra [acá](https://github.com/miguel-vila/haxl-poc/blob/master/src/main/scala/saxl/Example.scala#L85).

**@TODO:**

* [Una mejor estructura de tipos](https://github.com/miguel-vila/haxl-poc/blob/4febb9f694621946c99df1e7528dc3bfadf6a8bc/src/main/scala/saxl/Fetch.scala#L7) que permita un uso más fácil y que a su vez permita separar en distintos archivos la implementación.
* La inclusión y uso de alguna librería de macros que permita convertir llamadas a `flatMap` que ignoran el parámetro en llamadas a la función `ap` del aplicativo. Esencialmente la sección 7 del artículo. En su defecto [esto](https://github.com/puffnfresh/wartremover#noneedformonad) puede ser útil.
* [Esto](https://github.com/miguel-vila/haxl-poc/blob/de1af7dc46ab5202866c3b30b85c2732a5b0f4a4/src/main/scala/saxl/Example.scala#L126) 
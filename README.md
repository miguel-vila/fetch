# haxl poc

Una prueba de concepto para implementar [haxl](http://community.haskell.org/~simonmar/papers/haxl-icfp14.pdf) en Scala, tal vez ingenuamente. Usa la librería estándar de Futuros para la ejecución y scalaz para ayuda sintáctica de aplicativos.

Un ejemplo de su potencial uso, tomado del artículo, se encuentra [acá](https://github.com/miguel-vila/haxl-poc/blob/5b26a401313c3e9563aadec728f38ce57d22cee7/src/main/scala/saxl/Example.scala#L82).

**@TODO:**

* La inclusión y uso de alguna librería de macros que permita convertir llamadas a `flatMap` que ignoran el parámetro en llamadas a la función `ap` del aplicativo. Esencialmente la sección 7 del artículo. En su defecto [esto](https://github.com/puffnfresh/wartremover#noneedformonad) puede ser útil.
* Terminar de introducir cosas de la sección 8.1.
* Introducir cambios de la sección 9.1 del artículo.

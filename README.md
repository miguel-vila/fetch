# haxl poc

[![Build Status](https://travis-ci.org/miguel-vila/haxl-poc.svg?branch=master)](https://travis-ci.org/miguel-vila/haxl-poc)

Una prueba de concepto para implementar [haxl](http://community.haskell.org/~simonmar/papers/haxl-icfp14.pdf) en Scala, tal vez ingenuamente. Usa la librería estándar de Futuros para la ejecución y scalaz para ayuda sintáctica de aplicativos.

Un ejemplo de su potencial uso, tomado del artículo, se encuentra [acá](https://github.com/miguel-vila/haxl-poc/blob/37771fe7f3fdcd46d50d55aaf4376ac4eaf38f23/src/main/scala/saxl/Example.scala#L78).

**@TODO:**

* La inclusión y uso de alguna librería de macros que permita convertir llamadas a `flatMap` que ignoran el parámetro en llamadas a la función `ap` del aplicativo. Esencialmente la sección 7 del artículo. En su defecto [esto](https://github.com/puffnfresh/wartremover#noneedformonad) puede ser útil.
* Terminar de introducir cosas de la sección 8.1.
* Introducir cambios de la sección 9.1 del artículo.

## Ideas de Stitch

Algunas ideas provenientes de Stitch que se podrían incluir:

* Objetivos:

![Imgur](http://i.imgur.com/zLAp8nH.png)

* Modelo de ejecución:
![Imgur](http://i.imgur.com/INgfeae.png)

* Agrupación de queries:
![Imgur](http://i.imgur.com/Iezcc5l.png)

* Enmascarar reintentos:
![Imgur](http://i.imgur.com/OGDuk5R.png)

* n+1 problem -> hacer batch a nivel de SQL:
![Imgur](http://i.imgur.com/wCayzK4.png)



## Fuentes

* [There is no Fork: an Abstraction for Efficient, Concurrent, and Concise Data Access (Haxl's paper)](http://community.haskell.org/~simonmar/papers/haxl-icfp14.pdf)
* [Introducing Stitch](https://www.youtube.com/watch?v=VVpmMfT8aYw)
* [SBTB 2014, Jake Donham: Stitch, an Applicative Functor for Composing RPC Services](https://www.youtube.com/watch?v=bmIxIslimVY)
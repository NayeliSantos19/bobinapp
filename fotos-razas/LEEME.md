# Fotos de referencia de las razas

Pon aquí una foto por raza con el nombre exacto de la primera columna (sirve .jpg, .png o .webp) y ejecuta:

```
pip install pillow
python herramientas/preparar_fotos_razas.py
```

El script las endereza, las reduce a 1024 px y las copia a `android/app/src/main/assets/razas/`. Las razas sin foto siguen mostrando sus colores de pelaje.

## Licencias

Usa fotos con licencia libre (CC BY, CC BY-SA, CC0 o dominio público), por ejemplo de Wikimedia Commons: en la página de cada archivo aparecen el autor y la licencia. Anótalos en `creditos.csv` para que la app los muestre debajo de la foto, como pide la licencia:

```
id,autor,licencia,fuente
holstein,Nombre del autor,CC BY-SA 4.0,Wikimedia Commons
```

Busca un animal adulto, de lado, completo y con fondo despejado: es la vista que mejor muestra giba, orejas, cuernos y pelaje.

## Lista de razas (33)

| Archivo | Raza | Buscar foto |
|---|---|---|
| `brahman.jpg` | Brahman | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Brahman+cattle) |
| `brahman_rojo.jpg` | Brahman rojo | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Red+Brahman+cattle) |
| `nelore.jpg` | Nelore | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Nelore+cattle) |
| `gyr.jpg` | Gyr | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Gir+cattle) |
| `guzerat.jpg` | Guzerat | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Guzerat+cattle) |
| `sahiwal.jpg` | Sahiwal | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Sahiwal+cattle) |
| `indubrasil.jpg` | Indubrasil | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Indubrasil+cattle) |
| `angus.jpg` | Aberdeen Angus | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Aberdeen+Angus) |
| `angus_rojo.jpg` | Red Angus | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Red+Angus+cattle) |
| `hereford.jpg` | Hereford | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Hereford+cattle) |
| `charolais.jpg` | Charolais | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Charolais+cattle) |
| `simmental.jpg` | Simmental | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Simmental+cattle) |
| `limousin.jpg` | Limousin | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Limousin+cattle) |
| `shorthorn.jpg` | Shorthorn | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Shorthorn+cattle) |
| `wagyu.jpg` | Wagyu | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Wagyu+cattle) |
| `azul_belga.jpg` | Azul Belga | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Belgian+Blue+cattle) |
| `longhorn.jpg` | Texas Longhorn | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Texas+Longhorn) |
| `highland.jpg` | Highland | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Highland+cattle) |
| `holstein.jpg` | Holstein | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Holstein+Friesian+cow) |
| `jersey.jpg` | Jersey | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Jersey+cow) |
| `pardo_suizo.jpg` | Pardo Suizo | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Brown+Swiss+cow) |
| `guernsey.jpg` | Guernsey | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Guernsey+cow) |
| `ayrshire.jpg` | Ayrshire | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Ayrshire+cow) |
| `normando.jpg` | Normando | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Normande+cow) |
| `brangus.jpg` | Brangus | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Brangus+cattle) |
| `santa_gertrudis.jpg` | Santa Gertrudis | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Santa+Gertrudis+cattle) |
| `beefmaster.jpg` | Beefmaster | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Beefmaster+cattle) |
| `simbrah.jpg` | Simbrah | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Simbrah+cattle) |
| `girolando.jpg` | Girolando | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Girolando) |
| `senepol.jpg` | Senepol | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Senepol+cattle) |
| `reyna.jpg` | Reyna | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Reyna+creole+cattle) |
| `romosinuano.jpg` | Romosinuano | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Romosinuano) |
| `bon.jpg` | Blanco Orejinegro | [buscar en Wikimedia Commons](https://commons.wikimedia.org/w/index.php?title=Special:MediaSearch&type=image&search=Blanco+Orejinegro) |

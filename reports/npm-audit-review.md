# Revisión del informe npm audit — 2026-10-19

## Alcance y método

Se revisó el frontend autorizado en worktree detached `ebf7fbe723416a45ad43c3357b2a1a9c18c2151a`, repositorio `NESHrec/frontproyectodesarollo1`. Se ejecutó `npm.cmd ci` según el lockfile y luego `npm.cmd audit --json`. No se ejecutaron `npm audit fix` ni `npm update`; no se cambiaron `package.json` o `package-lock.json`.

`npm ci`: exit 0, 422 paquetes añadidos y 423 auditados. La instalación reportó 7 vulnerabilidades altas y avisos de que los scripts de instalación de `esbuild` y `unrs-resolver` no fueron aprobados/ejecutados por la política de npm. Esto no impidió instalar ni ejecutar las comprobaciones documentadas.

`npm.cmd audit --json`: exit 1 porque el informe encontró vulnerabilidades. El comando produjo un JSON válido; no hubo fallo de red, registro o herramienta. La documentación de npm especifica el código no cero para auditorías con vulnerabilidades. El resultado sanitizado se conserva fuera de Git en `runtime/cs062-npm-audit-20261019/npm-audit-sanitized.json`.

## Conteo y relación entre paquetes y avisos

El resultado actual es **7 paquetes/nodos afectados, todos de severidad alta, y 3 avisos únicos**. El registro anterior también mostraba “7 high”; no aumentó el total reportado. Esta revisión aclara que no son siete problemas independientes: un mismo aviso transitivo se refleja en varios paquetes de la cadena ESLint. El JSON sanitizado contiene metadatos y rutas, no credenciales ni archivos de entorno.

| Aviso | Paquete afectado / versión observada | Rango afectado y versión corregida según advisory | Dependencia observada | Impacto descrito por fuente; explotabilidad en Clínica Serena |
|---|---|---|---|---|
| GHSA-wq5f-xc86-pv6w / CVE-2026-96889 | `sharp@0.35.4` | `<0.35.5`; corregido en `0.35.5` | opcional bajo `next@16.3.8` | El aviso atribuye un problema de memoria en librsvg y posible RCE bajo condiciones runtime concretas, en Linux glibc y con decodificación SVG. No se probó una ruta explotable en esta aplicación ni se verificó su entorno de despliegue. |
| GHSA-68fv-2mgg-jv7q / CVE-2026-93749 | `source-map-js@1.2.1` | `>=1.0.0 <1.2.2`; corregido en `1.2.2` | transitivo por herramientas Tailwind/PostCSS, PostCSS y dependencias asociadas a CSS/jsdom | El aviso describe bloqueo síncrono del event loop ante source maps indexados con offsets de línea muy grandes. La exposición de la aplicación en runtime no se demostró; aparecen varias rutas de build/test. |
| GHSA-vfj7-8cjw-p6xm / CVE-2026-93687 | `braces@3.0.3` | `<=3.0.3`; el advisory consultado no lista versión corregida | `braces → micromatch@4.0.8 → fast-glob@3.3.1 → @next/eslint-plugin-next@16.3.5 → eslint-config-next@16.3.5` (devDependency directa) | El aviso describe agotamiento de stack por patrones de llaves muy anidados. La cadena observada es herramienta de lint/desarrollo; no se acreditó uso de entradas de usuario en producción. |

El agregador de npm enumera también `@next/eslint-plugin-next`, `eslint-config-next`, `fast-glob` y `micromatch` como nodos afectados porque forman parte de la cadena que trae `braces`; no equivalen a cuatro avisos adicionales. `npm explain`/`npm ls` se usaron para inspeccionar esas rutas.

## Correcciones disponibles y decisión

Las fuentes consultadas indican una actualización compatible de `sharp` a 0.35.5 y de `source-map-js` a 1.2.2. Para `braces`, el advisory no declara parche. La sugerencia automática de npm para la cadena de ESLint propone `eslint-config-next@14.2.35` e indica cambio semver mayor, lo que representa un downgrade mayor respecto de la versión actual; no se aplicó. La selección de actualización o mitigación requiere una tarea de dependencias separada y pruebas de compatibilidad.

La severidad “high” es la clasificación del aviso, no evidencia de explotación. La transitividad, el entorno de despliegue y la posibilidad de que datos controlados por atacante alcancen la función vulnerable requieren evaluación específica. No se realizaron pruebas ofensivas ni se afirma explotabilidad.

## Fuentes oficiales revisadas

- [GitHub Advisory GHSA-wq5f-xc86-pv6w (sharp)](https://github.com/advisories/GHSA-wq5f-xc86-pv6w)
- [GitHub Advisory GHSA-68fv-2mgg-jv7q (source-map-js)](https://github.com/advisories/GHSA-68fv-2mgg-jv7q)
- [GitHub Advisory GHSA-vfj7-8cjw-p6xm (braces)](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm)
- [Documentación de npm audit](https://docs.npmjs.com/cli/commands/npm-audit/)
- [Documentación de npm explain](https://docs.npmjs.com/cli/v7/commands/npm-explain/)

## Dictamen de esta revisión

Auditoría ejecutada y analizable: 7 nodos de paquetes afectados, 3 avisos altos distintos, sin evidencia reunida para afirmar explotación en el producto. Se deja como riesgo pendiente de evaluación y remediación de dependencias. No se cambió el árbol de dependencias. Ver `CS-062-production-readiness.md` para distinguir este riesgo del alcance de CS-062.

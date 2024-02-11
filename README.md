# Stianloader-concurrent

An assortment of highly-specialized structures for use in a concurrent environment.

## Why not use ConcurrentHashMap?

Many of the data structures implemented in this library could easily be replaced by ConcurrentHashMap,
however for as long as Valhalla is not a thing ConcurrentHashMap is more expensive than our solutions
due to overheads that are incurred by autoboxing or simply the more design of the CHM.

## Contributing

All contributions of any kind are extremely welcome! Feel free to suggest, add, improve or outright redo sections of this library.
Contributors are extremely rare within this desert so don't be shy to contribute even though you think you lack the expertise.
Conversely, experts shouldn't stand idly waiting for the library to improve on it's own - I assume you already know that this does
not happen.

However, when contributing please make sure your contribution would be compatible with your license.

## Licensing

This library is licensed under the MIT License.

## Maven

The library is still pretty much in early development but it can be found under the `https://stianloader.org/maven` repository
as `org.stianloader:stianloader-concurrent:<version>` the available versions are listed under
https://stianloader.org/maven/org/stianloader/stianloader-concurrent/

## Dependencies

This library depends on Java 8+ and Fastutil. Latter is used in order to allow to quickly swap between implementations of your
data types.

## Alternatives

A known alternative to this library is `fastutil-concurrent-wrapper`, however that library does not support iterators.

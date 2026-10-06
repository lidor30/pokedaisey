"""Where the generator scripts find the built pret decomp checkouts: $DECOMPS."""
import os
import sys


def decomp(*parts):
    root = os.environ.get("DECOMPS")
    if not root:
        sys.exit("DECOMPS isn't set: point it at the directory holding the built decomp "
                 "checkouts (pokefirered/, pokeemerald/, pokeruby/, pokehns/, ...)")
    return os.path.join(root, *parts)

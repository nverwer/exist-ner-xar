# exist-ner-xar

A package that provides a function for Named Entity Recognition (NER).

## Usage

It is assumed that you have already installed eXist-db and Maven.

Download or clone or fork the git repository, which has all the source files.
In the directory where you have just downloaded the project, do `maven install`. 
This will create a .xar file in the `target` directory.

Start eXist-db.

Install the .xar file into eXist, using the package manager.

Open eXide, and enter the following in a new XQuery document.

```
import module namespace ner = "http://rakensi.com/exist-db/xquery/functions/ner";

let $grammar :=
  <grammar>
    <entity id="♳"><name>PET</name><name>polyethylene</name><name>terephthalate</name></entity>
    <entity id="♴"><name>HDPE</name><name>high-density polyethylene</name><name>polyethylene high-density</name></entity>
    <entity id="♵"><name>PVC</name><name>polyvinyl chloride</name><name>polyvinylchloride</name><name>vinyl</name><name>polyvinyl</name></entity>
  </grammar>
let $input := ``[
  RIC for vinyl and polyethylene
]``
let $ner-parse := ner:named-entity-recognition($grammar, <ric id=""/>, map{})
return $ixml-parse($input)
```

Press the 'Eval' button. The output should look like:

```
<wrapper>
  RIC for <ric id="♵">vinyl</ric> and <ric id="♳">polyethylene</ric>
</wrapper>
```

The `<wrapper>` needs to go away. I am working on that. This is an intermediate version that shows how this could work.

```
import module namespace ner = "http://rakensi.com/exist-db/xquery/functions/ner";

let $grammar :=
  <grammar>
    <entity id="THE"><name>THE</name></entity>
    <entity id="CF"><name>CF</name><name>C F</name></entity>
    <entity id="RSVP"><name>rsvp</name><name>R S V P</name></entity>
  </grammar>
let $input := ``[c.f. the r.s.v.p.]``

let $ner-parse := ner:named-entity-recognition($grammar, <ntt id=""/>,
  map{'case-insensitive-min-length': 3, 'fuzzy-min-length': 3, 'word-chars': ''})
return $ner-parse($input)
```

## Notes

# exist-ner-xar

An eXist-db package that provides a function for Named Entity Recognition (NER).

## Building from source

It is assumed that you have already installed eXist-db and Maven.

Download or clone or fork the git repository, which has all the source files.
In the directory where you have just downloaded the project, do `maven install`. 
This will create a .xar file in the `target` directory.

## Installing

Start eXist-db.

Install the `exist-ner-xar-###.xar` file into eXist, using the package manager.

## Using

Open eXide, and enter the following in a new XQuery document.

```
import module namespace ner = "http://rakensi.com/xquery/functions/ner";
let $grammar :=
  <grammar>
    <entity id="♳"><name>PET</name><name>polyethylene</name><name>terephthalate</name></entity>
    <entity id="♴"><name>HDPE</name><name>high-density polyethylene</name><name>polyethylene high-density</name></entity>
    <entity id="♵"><name>PVC</name><name>polyvinyl chloride</name><name>polyvinylchloride</name><name>vinyl</name><name>polyvinyl</name></entity>
  </grammar>
let $input := <r>RIC for vinyl and polyethylene</r>
let $ner-parse := ner:named-entity-recognition($grammar, map{'match-element-name': 'ric', 'match-attribute': 'symbol'})
return $ner-parse($input)
```

Press the 'Eval' button. The output should look like:

```
<r>RIC for <ric symbol="♵">vinyl</ric> and <ric symbol="♳">polyethylene</ric></r>
```

## See also

For more documentation, please have a look at the [XML-NER project](https://github.com/nverwer/XML-NER).

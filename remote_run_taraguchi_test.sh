sed -i '1s/^\xEF\xBB\xBF//' /kh_code/lila/modules/round/src/test/OmokTaraguchiOpeningTest.scala
cd /kh_code/lila || exit 1
sbt -batch "round/testOnly lila.round.OmokTaraguchiOpeningTest"
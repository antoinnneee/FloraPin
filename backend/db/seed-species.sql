-- Seed initial du catalogue d'espèces (NODE-126).
-- ~90 espèces communes (flore sauvage + jardin, surtout tempérée/FR).
-- Idempotent : ON CONFLICT (scientific_name) ne réinsère pas un doublon, donc
-- le fichier peut être rejoué sans risque. Sert de base à l'autocomplétion dès
-- le premier lancement (cf. backend/db/schema.sql, table `species`).
--
-- Appliqué au premier démarrage de la base via docker-entrypoint-initdb.d
-- (02-seed-species.sql), après le schéma. L'emoji suit le référentiel visuel de
-- l'app (FlowerEmoji) : 🌸 défaut, 🌻 tournesol, 🌹 rose, 🌷 tulipe,
-- 🌼 marguerite/composées, 🌺 hibiscus, 🌵 succulentes, 🪷 nénuphar.

INSERT INTO species (scientific_name, common_name, family, emoji) VALUES
  -- Rosaceae
  ('Rosa canina',            'Églantier',                'Rosaceae',      '🌹'),
  ('Rosa gallica',           'Rosier de France',         'Rosaceae',      '🌹'),
  ('Rosa rugosa',            'Rosier rugueux',           'Rosaceae',      '🌹'),
  ('Prunus avium',           'Merisier',                 'Rosaceae',      '🌸'),
  ('Prunus spinosa',         'Prunellier',               'Rosaceae',      '🌸'),
  ('Prunus serrulata',       'Cerisier du Japon',        'Rosaceae',      '🌸'),
  ('Malus domestica',        'Pommier',                  'Rosaceae',      '🌸'),
  ('Crataegus monogyna',     'Aubépine monogyne',        'Rosaceae',      '🌸'),
  ('Fragaria vesca',         'Fraisier des bois',        'Rosaceae',      '🌸'),
  ('Potentilla reptans',     'Potentille rampante',      'Rosaceae',      '🌼'),
  ('Filipendula ulmaria',    'Reine-des-prés',           'Rosaceae',      '🌸'),
  ('Geum urbanum',           'Benoîte commune',          'Rosaceae',      '🌼'),

  -- Asteraceae (composées)
  ('Bellis perennis',        'Pâquerette',               'Asteraceae',    '🌼'),
  ('Leucanthemum vulgare',   'Marguerite commune',       'Asteraceae',    '🌼'),
  ('Helianthus annuus',      'Tournesol',                'Asteraceae',    '🌻'),
  ('Taraxacum officinale',   'Pissenlit',                'Asteraceae',    '🌼'),
  ('Matricaria chamomilla',  'Camomille sauvage',        'Asteraceae',    '🌼'),
  ('Achillea millefolium',   'Achillée millefeuille',    'Asteraceae',    '🌼'),
  ('Centaurea cyanus',       'Bleuet',                   'Asteraceae',    '🌸'),
  ('Cichorium intybus',      'Chicorée sauvage',         'Asteraceae',    '🌸'),
  ('Calendula officinalis',  'Souci officinal',          'Asteraceae',    '🌼'),
  ('Tanacetum vulgare',      'Tanaisie commune',         'Asteraceae',    '🌼'),
  ('Cirsium arvense',        'Cirse des champs',         'Asteraceae',    '🌸'),
  ('Senecio vulgaris',       'Séneçon commun',           'Asteraceae',    '🌼'),
  ('Tussilago farfara',      'Tussilage',                'Asteraceae',    '🌼'),
  ('Solidago virgaurea',     'Verge d''or',              'Asteraceae',    '🌼'),
  ('Cosmos bipinnatus',      'Cosmos',                   'Asteraceae',    '🌸'),
  ('Dahlia pinnata',         'Dahlia',                   'Asteraceae',    '🌸'),

  -- Liliaceae / Amaryllidaceae / Iridaceae (monocotylédones à fleurs)
  ('Tulipa gesneriana',      'Tulipe des jardins',       'Liliaceae',     '🌷'),
  ('Lilium candidum',        'Lis blanc',                'Liliaceae',     '🌸'),
  ('Narcissus pseudonarcissus','Jonquille',              'Amaryllidaceae','🌼'),
  ('Galanthus nivalis',      'Perce-neige',              'Amaryllidaceae','🌸'),
  ('Crocus vernus',          'Crocus de printemps',      'Iridaceae',     '🌸'),
  ('Iris pseudacorus',       'Iris des marais',          'Iridaceae',     '🌸'),
  ('Hyacinthus orientalis',  'Jacinthe',                 'Asparagaceae',  '🌸'),
  ('Muscari armeniacum',     'Muscari',                  'Asparagaceae',  '🌸'),
  ('Convallaria majalis',    'Muguet',                   'Asparagaceae',  '🌸'),

  -- Papaveraceae / Ranunculaceae
  ('Papaver rhoeas',         'Coquelicot',               'Papaveraceae',  '🌺'),
  ('Chelidonium majus',      'Grande chélidoine',        'Papaveraceae',  '🌼'),
  ('Ranunculus acris',       'Bouton d''or',             'Ranunculaceae', '🌼'),
  ('Anemone nemorosa',       'Anémone des bois',         'Ranunculaceae', '🌸'),
  ('Clematis vitalba',       'Clématite des haies',      'Ranunculaceae', '🌸'),
  ('Helleborus niger',       'Rose de Noël',             'Ranunculaceae', '🌸'),
  ('Aquilegia vulgaris',     'Ancolie commune',          'Ranunculaceae', '🌸'),
  ('Nigella damascena',      'Nigelle de Damas',         'Ranunculaceae', '🌸'),

  -- Lamiaceae (labiées)
  ('Lavandula angustifolia', 'Lavande vraie',            'Lamiaceae',     '🌸'),
  ('Salvia pratensis',       'Sauge des prés',           'Lamiaceae',     '🌸'),
  ('Thymus vulgaris',        'Thym commun',              'Lamiaceae',     '🌸'),
  ('Mentha spicata',         'Menthe verte',             'Lamiaceae',     '🌸'),
  ('Lamium album',           'Lamier blanc',             'Lamiaceae',     '🌸'),
  ('Glechoma hederacea',     'Lierre terrestre',         'Lamiaceae',     '🌸'),
  ('Origanum vulgare',       'Origan',                   'Lamiaceae',     '🌸'),
  ('Rosmarinus officinalis', 'Romarin',                  'Lamiaceae',     '🌸'),

  -- Fabaceae (légumineuses)
  ('Trifolium pratense',     'Trèfle des prés',          'Fabaceae',      '🌸'),
  ('Trifolium repens',       'Trèfle blanc',             'Fabaceae',      '🌼'),
  ('Lotus corniculatus',     'Lotier corniculé',         'Fabaceae',      '🌼'),
  ('Vicia cracca',           'Vesce cracca',             'Fabaceae',      '🌸'),
  ('Lupinus polyphyllus',    'Lupin',                    'Fabaceae',      '🌸'),
  ('Wisteria sinensis',      'Glycine de Chine',         'Fabaceae',      '🌸'),
  ('Cytisus scoparius',      'Genêt à balais',           'Fabaceae',      '🌼'),

  -- Brassicaceae / Violaceae / Primulaceae
  ('Cardamine pratensis',    'Cardamine des prés',       'Brassicaceae',  '🌸'),
  ('Sinapis arvensis',       'Moutarde des champs',      'Brassicaceae',  '🌼'),
  ('Viola odorata',          'Violette odorante',        'Violaceae',     '🌸'),
  ('Viola tricolor',         'Pensée sauvage',           'Violaceae',     '🌸'),
  ('Primula veris',          'Primevère officinale',     'Primulaceae',   '🌼'),
  ('Primula vulgaris',       'Primevère acaule',         'Primulaceae',   '🌼'),
  ('Cyclamen hederifolium',  'Cyclamen de Naples',       'Primulaceae',   '🌸'),

  -- Campanulaceae / Boraginaceae / Convolvulaceae
  ('Campanula rotundifolia', 'Campanule à feuilles rondes','Campanulaceae','🌸'),
  ('Myosotis arvensis',      'Myosotis des champs',      'Boraginaceae',  '🌸'),
  ('Borago officinalis',     'Bourrache officinale',     'Boraginaceae',  '🌸'),
  ('Symphytum officinale',   'Consoude officinale',      'Boraginaceae',  '🌸'),
  ('Convolvulus arvensis',   'Liseron des champs',       'Convolvulaceae','🌸'),

  -- Onagraceae / Malvaceae / Geraniaceae / Apiaceae
  ('Epilobium angustifolium','Épilobe en épi',           'Onagraceae',    '🌸'),
  ('Malva sylvestris',       'Mauve sylvestre',          'Malvaceae',     '🌺'),
  ('Althaea officinalis',    'Guimauve officinale',      'Malvaceae',     '🌺'),
  ('Hibiscus syriacus',      'Althéa',                   'Malvaceae',     '🌺'),
  ('Geranium robertianum',   'Herbe à Robert',           'Geraniaceae',   '🌸'),
  ('Daucus carota',          'Carotte sauvage',          'Apiaceae',      '🌼'),
  ('Heracleum sphondylium',  'Berce commune',            'Apiaceae',      '🌼'),

  -- Aquatiques
  ('Nymphaea alba',          'Nénuphar blanc',           'Nymphaeaceae',  '🪷'),
  ('Nelumbo nucifera',       'Lotus sacré',              'Nelumbonaceae', '🪷'),

  -- Succulentes / cactées
  ('Sempervivum tectorum',   'Joubarbe des toits',       'Crassulaceae',  '🌵'),
  ('Sedum acre',             'Orpin âcre',               'Crassulaceae',  '🌵'),
  ('Opuntia ficus-indica',   'Figuier de Barbarie',      'Cactaceae',     '🌵'),
  ('Echinopsis oxygona',     'Oursin',                   'Cactaceae',     '🌵'),
  ('Aloe vera',              'Aloès',                    'Asphodelaceae', '🌵'),

  -- Divers ligneux/grimpants communs
  ('Syringa vulgaris',       'Lilas commun',             'Oleaceae',      '🌸'),
  ('Jasminum officinale',    'Jasmin officinal',         'Oleaceae',      '🌸'),
  ('Hedera helix',           'Lierre grimpant',          'Araliaceae',    '🌼'),
  ('Digitalis purpurea',     'Digitale pourpre',         'Plantaginaceae','🌸'),
  ('Verbascum thapsus',      'Bouillon-blanc',           'Scrophulariaceae','🌼'),
  ('Hypericum perforatum',   'Millepertuis perforé',     'Hypericaceae',  '🌼')
ON CONFLICT (scientific_name) DO NOTHING;

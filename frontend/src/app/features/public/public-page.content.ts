/** Static copy for the public (no-login) informational pages linked from the
 *  login screen — required so Razorpay's business-verification reviewers can
 *  see Amazing Logistics' business/contact/policy details without an account.
 *  Real business facts (address/email/phone/GSTIN) as supplied by the business
 *  owner; policy figures (refund window, etc.) are standard industry defaults —
 *  review before relying on them as binding legal text. */

export const BUSINESS = {
  legalName: 'Amazing Logistics',
  supportEmail: 'ashwin@amazinglpl.com',
  supportPhone: '+91 93710 09372',
  gstin: '27AFNPH9732E1Z8',
  address: ['BEHIND HANS TRAVELS, PARKING NO 3, SANGAMWADI', 'Pune, Maharashtra — 411003', 'India'] as string[]
} as const;

export type PublicPageKey =
  | 'home' | 'services' | 'pricing' | 'about' | 'contact'
  | 'terms' | 'privacy' | 'refund-policy' | 'shipping-policy' | 'payment-policy';

export interface PublicPageSection { heading: string; body: string[]; }
export interface PublicPageContent { title: string; intro: string; sections: PublicPageSection[]; }

export const PUBLIC_PAGE_LINKS: { key: PublicPageKey; label: string }[] = [
  { key: 'home', label: 'Home' },
  { key: 'services', label: 'Services' },
  { key: 'pricing', label: 'Pricing' },
  { key: 'about', label: 'About Us' },
  { key: 'contact', label: 'Contact Us' },
  { key: 'terms', label: 'Terms & Conditions' },
  { key: 'privacy', label: 'Privacy Policy' },
  { key: 'refund-policy', label: 'Refund/Cancellation Policy' },
  { key: 'shipping-policy', label: 'Shipping & Delivery Policy' },
  { key: 'payment-policy', label: 'Payment Policy' }
];

export const PUBLIC_PAGE_CONTENT: Record<PublicPageKey, PublicPageContent> = {
  home: {
    title: 'Amazing Logistics',
    intro: 'A courier and logistics service provider based in Pune, Maharashtra, offering domestic parcel pickup, delivery and shipment tracking for individuals and businesses.',
    sections: [
      { heading: 'What we do', body: [
        'Amazing Logistics runs pickup, transportation and delivery of parcels and consignments across India, with branch and hub operations, real-time shipment tracking and dedicated support for business (B2B) customers.'
      ] },
      { heading: 'Get in touch', body: [
        `Have a shipment, a query, or need a quote? Reach us at ${BUSINESS.supportEmail} or ${BUSINESS.supportPhone} — see Contact Us for full details.`
      ] }
    ]
  },
  services: {
    title: 'Services',
    intro: 'Amazing Logistics offers the following core courier and logistics services.',
    sections: [
      { heading: 'Domestic Parcel Delivery', body: [
        'Pickup and delivery of documents and parcels across serviceable pin codes in India, with branch-to-branch and hub-based routing.'
      ] },
      { heading: 'Express & Standard Delivery', body: [
        'Multiple service speeds depending on route and urgency — choose the option that fits your timeline and budget at the time of booking.'
      ] },
      { heading: 'Cash on Delivery (COD)', body: [
        'COD collection is available on eligible shipments, with the collected amount remitted to the sender as per standard processing timelines.'
      ] },
      { heading: 'Business (B2B) Shipping', body: [
        'Bulk booking, wallet-based billing and dedicated account support for businesses shipping regularly through our network.'
      ] },
      { heading: 'Shipment Tracking', body: [
        'Every booking gets a tracking number so senders and recipients can follow a shipment through pickup, transit and delivery.'
      ] }
    ]
  },
  pricing: {
    title: 'Pricing',
    intro: 'Shipping charges depend on parcel weight, distance/route, delivery speed and any additional services selected (e.g. COD, insurance).',
    sections: [
      { heading: 'How pricing works', body: [
        'Rates are computed per shipment at the time of booking based on origin, destination, weight/volumetric weight and the service level chosen.',
        'Registered business customers may have negotiated rate cards; individual/walk-in customers are quoted the standard rate card in force at booking.'
      ] },
      { heading: 'Request a quote', body: [
        `For a rate estimate before booking, contact us at ${BUSINESS.supportEmail} or ${BUSINESS.supportPhone} with your pickup/delivery pin codes and approximate weight.`
      ] }
    ]
  },
  about: {
    title: 'About Us',
    intro: `${BUSINESS.legalName} is a courier and logistics service provider headquartered in Pune, Maharashtra, India.`,
    sections: [
      { heading: 'Who we are', body: [
        `${BUSINESS.legalName} provides parcel pickup, transportation and delivery services, serving both individual customers and business accounts across India through a network of branches and hubs.`
      ] },
      { heading: 'Registered office', body: BUSINESS.address },
      { heading: 'Business registration', body: [ `GSTIN: ${BUSINESS.gstin}` ] }
    ]
  },
  contact: {
    title: 'Contact Us',
    intro: 'We are happy to help with bookings, tracking queries, billing or general questions.',
    sections: [
      { heading: 'Support email', body: [ BUSINESS.supportEmail ] },
      { heading: 'Support phone', body: [ BUSINESS.supportPhone ] },
      { heading: 'Registered address', body: BUSINESS.address }
    ]
  },
  terms: {
    title: 'Terms & Conditions',
    intro: `These Terms & Conditions govern the use of ${BUSINESS.legalName}'s courier and logistics services ("Service"). By booking a shipment with us, you agree to these terms.`,
    sections: [
      { heading: 'Booking & acceptance', body: [
        'A booking is confirmed once accepted at pickup and a consignment/tracking number is issued. We may decline items that are prohibited, unsafe, mis-declared, or inadequately packed.'
      ] },
      { heading: 'Delivery timelines', body: [
        'Delivery timelines shown at booking are estimates, not guarantees, and may be affected by weather, transport disruptions, incomplete/incorrect addresses, or events beyond our reasonable control.'
      ] },
      { heading: 'Liability', body: [
        'Our liability for loss or damage to a shipment is limited as per the terms agreed at booking (and any declared value/insurance opted for). We are not liable for indirect or consequential loss.'
      ] },
      { heading: 'Prohibited items', body: [
        'Items prohibited by applicable law or common carrier practice (e.g. hazardous, illegal, or restricted goods) may not be shipped through our Service.'
      ] },
      { heading: 'Governing law', body: [
        'These terms are governed by the laws of India, with courts in Pune, Maharashtra having exclusive jurisdiction over any dispute.'
      ] },
      { heading: 'Contact', body: [ `Questions about these terms: ${BUSINESS.supportEmail}` ] }
    ]
  },
  privacy: {
    title: 'Privacy Policy',
    intro: `${BUSINESS.legalName} collects and uses personal information only as needed to provide courier and logistics services.`,
    sections: [
      { heading: 'Information we collect', body: [
        'Name, contact number, email and address details of senders and recipients; shipment details (contents description, weight, declared value); and payment/billing information needed to process a booking.'
      ] },
      { heading: 'How we use it', body: [
        'To create and fulfil bookings, provide tracking updates, process payments and COD remittance, respond to support queries, and meet legal/regulatory record-keeping requirements.'
      ] },
      { heading: 'Sharing', body: [
        'We do not sell personal information. Data is shared only with delivery partners/branches as needed to complete a shipment, and with payment processors (e.g. Razorpay) to process payments, or where required by law.'
      ] },
      { heading: 'Security', body: [
        'We apply reasonable technical and organisational measures to protect customer data against unauthorised access, loss or misuse.'
      ] },
      { heading: 'Your rights', body: [
        `To access, correct or request deletion of your personal information, contact us at ${BUSINESS.supportEmail}.`
      ] }
    ]
  },
  'refund-policy': {
    title: 'Refund / Cancellation Policy',
    intro: 'This policy covers cancellation of a booking and refund of amounts paid to Amazing Logistics.',
    sections: [
      { heading: 'Cancellation before pickup', body: [
        'A booking can be cancelled free of charge any time before the shipment is picked up. Any amount already paid for that booking will be refunded in full.'
      ] },
      { heading: 'After pickup / in transit', body: [
        'Once a shipment has been picked up, the booking cannot be cancelled. If delivery cannot be completed due to a fault on our part, a refund or re-delivery will be arranged.'
      ] },
      { heading: 'Refund method & timeline', body: [
        'Approved refunds are credited back to the original payment method (or wallet, where applicable) within 7–10 business days of approval.'
      ] },
      { heading: 'Damaged / lost shipments', body: [
        'Claims for damaged or lost shipments should be raised with proof (photos, invoice, tracking number) as soon as possible; eligible claims are settled as per the liability terms in our Terms & Conditions.'
      ] },
      { heading: 'How to request', body: [
        `Email ${BUSINESS.supportEmail} or call ${BUSINESS.supportPhone} with your tracking/booking number to request a cancellation or refund.`
      ] }
    ]
  },
  'shipping-policy': {
    title: 'Shipping & Delivery Policy',
    intro: 'How pickup, transit and delivery work for shipments booked with Amazing Logistics.',
    sections: [
      { heading: 'Serviceable areas', body: [
        'We service pin codes covered by our branch and hub network across India; serviceability for a specific route can be confirmed at the time of booking.'
      ] },
      { heading: 'Delivery timelines', body: [
        'Delivery timelines depend on origin, destination and the service level chosen at booking, and are estimates rather than guarantees.'
      ] },
      { heading: 'Packaging', body: [
        'Senders are responsible for adequately packing items to withstand normal transit handling. We may decline items that are inadequately packed or unsafe to transport.'
      ] },
      { heading: 'Delays', body: [
        'Occasional delays can occur due to weather, transport disruption, incomplete/incorrect addresses, customs/regulatory holds, or other events beyond our control.'
      ] },
      { heading: 'Tracking & support', body: [
        `Every shipment gets a tracking number. For a delayed or undelivered shipment, contact ${BUSINESS.supportEmail} or ${BUSINESS.supportPhone}.`
      ] }
    ]
  },
  'payment-policy': {
    title: 'Payment Policy',
    intro: 'How payments for bookings, billing and COD remittance work at Amazing Logistics.',
    sections: [
      { heading: 'Accepted payment methods', body: [
        'Online payments (UPI, debit/credit cards, net banking, wallets) via Razorpay, and Cash on Delivery (COD) where offered for a shipment.'
      ] },
      { heading: 'Business accounts', body: [
        'Registered business customers may use a prepaid wallet billed against their bookings, per the terms agreed with their account.'
      ] },
      { heading: 'Invoicing & GST', body: [
        `GST is charged as applicable on our services. Our GSTIN is ${BUSINESS.gstin}. Invoices are made available for completed shipments.`
      ] },
      { heading: 'Failed / duplicate payments', body: [
        `If a payment is deducted but a booking is not confirmed, or a duplicate charge occurs, contact ${BUSINESS.supportEmail} with the payment reference for resolution.`
      ] }
    ]
  }
};
